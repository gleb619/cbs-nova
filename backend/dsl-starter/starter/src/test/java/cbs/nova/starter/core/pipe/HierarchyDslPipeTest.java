package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import cbs.nova.starter.security.ManifestObjectGuard;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HierarchyDslPipeTest {

  private static int defaultMaxEventsPerRun() {
    return new DryRunProperties(null, null).log().maxEventsPerRun();
  }

  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry(
          Caffeine.newBuilder().build());
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null, null);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void finalReportAggregatesAllStageContributionsInOnePass() {
    GlobalManager.globalManager().registerHelper("auditLog",
            (cbs.nova.dsl.Executable<Object, Object>) ctx -> Result.success("audited"));
    GlobalManager.globalManager().registerProcess(
            Dsl.process("HierarchyProcess")
                    .input(Object.class)
                    .output(Object.class)
                    .execute(ctx -> Result.success("ok"))
                    .preview(ctx -> {
                      ctx.runHelper("auditLog");
                      return Result.success(null);
                    })
                    .compensation((ctx, history) -> ctx.log("rolled back"))
                    .build());

    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    when(recorder.finishRun("run-hierarchy")).thenReturn(List.of(
            new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L,
                    Map.of())));
    HierarchyDslPipe hierarchyPipe = newPipe(recorder);

    Result<HierarchyReport> result = hierarchyPipe.execute("HierarchyProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.HIERARCHY)
                    .runId("run-hierarchy").build());

    assertThat(result.isSuccess()).isTrue();
    HierarchyReport report = result.value();
    assertThat(report.name()).isEqualTo("HierarchyProcess");
    assertThat(report.dslDescriptor()).isNotNull();
    assertThat(report.hasCompensation()).isTrue();
    assertThat(report.executionTrace()).contains("called helper: auditLog");
    assertThat(report.astTree()).isNotNull();
    assertThat(report.astTree().name()).isEqualTo("HierarchyProcess");
    assertThat(report.astTree().kind()).isEqualTo(CallKind.PROCESS);
    assertThat(report.metrics()).isNotNull();
    assertThat(report.metrics().callCounts()).containsEntry(CallKind.PROCESS, 1);
    assertThat(report.externalCalls()).hasSize(1);
    assertThat(report.callCounts())
            .containsEntry(ExternalCallRecorder.TYPE_DATABASE, 1);
    assertThat(report.dryRunLogs()).isNotNull();
    assertThat(report.errors()).isEmpty();
    assertThat(report.mermaidDiagram()).isNotBlank();
  }

  @Test
  void failedRunSurfacesErrorInReport() {
    HierarchyDslPipe hierarchyPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<HierarchyReport> result = hierarchyPipe.execute("MissingProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.HIERARCHY).runId("run-fail")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    HierarchyReport report = result.value();
    assertThat(report.errors()).hasSize(1);
    assertThat(report.errors().get(0).message())
            .contains("No DSL entity registered: MissingProcess");
  }

  private HierarchyDslPipe newPipe(ExternalCallRecorder recorder) {
    return newPipe(recorder, null);
  }

  private HierarchyDslPipe newPipe(ExternalCallRecorder recorder, ManifestObjectGuard objectGuard) {
    return new HierarchyDslPipe(recorder, dryRunLoggingContext, bufferRegistry,
            defaultMaxEventsPerRun(), previewProperties,
            new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()),
            new SimpleMeterRegistry(), new HierarchyDiagramRenderer(), null, objectGuard);
  }

  @Test
  void objectGuardDenialSurfacesInReportErrors() {
    GlobalManager.globalManager().registerHelper("auditLog",
            (cbs.nova.dsl.Executable<Object, Object>) ctx -> Result.success("audited"));
    GlobalManager.globalManager().registerProcess(
            Dsl.process("GuardedHierarchyProcess")
                    .input(Object.class)
                    .output(Object.class)
                    .execute(ctx -> Result.success("ok"))
                    .preview(ctx -> {
                      Result<?> helperResult = ctx.runHelper("auditLog");
                      if (!helperResult.isSuccess()) {
                        throw new IllegalStateException(helperResult.cause());
                      }
                      return Result.success(null);
                    })
                    .build());

    ManifestObjectGuard guard = mock(ManifestObjectGuard.class);
    when(guard.check(eq(ExecutionMode.HIERARCHY), any(), eq("helper"), eq("auditLog"),
            any())).thenReturn(Optional.of(
                    new ManifestObjectGuard.Denial("GuardedHierarchyProcess", "piece-1", "helper",
                            "auditLog", "explicit deny piece matched")));

    HierarchyDslPipe hierarchyPipe = newPipe(mock(ExternalCallRecorder.class), guard);

    Result<HierarchyReport> result = hierarchyPipe.execute("GuardedHierarchyProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.HIERARCHY).runId("run-guard")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().errors()).hasSize(1);
    assertThat(result.value().errors().get(0).message()).contains("Capability denied");
  }
}
