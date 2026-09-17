package cbs.nova.starter.core.pipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainDslPipeTest {

  private static int defaultMaxEventsPerRun() {
    return new DryRunProperties(null, null).log().maxEventsPerRun();
  }

  private final ContextFactory contextFactory = new ContextFactory();
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
            Dsl.process("ExplainProcess")
                    .input(Object.class)
                    .output(Object.class)
                    .execute(ctx -> Result.success("ok"))
                    .explain(ctx -> {
                      ctx.runHelper("auditLog");
                      return Result.success(null);
                    })
                    .compensation((ctx, history) -> ctx.log("rolled back"))
                    .build());

    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    when(recorder.finishRun("run-explain")).thenReturn(List.of(
            new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L,
                    Map.of())));
    ExplainDslPipe explainPipe = newPipe(recorder);

    Result<ExplainGraphReport> result = explainPipe.execute("ExplainProcess",
            contextFactory.of("payload", ExecutionMode.EXPLAIN, "run-explain"));

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport report = result.value();
    assertThat(report.name()).isEqualTo("ExplainProcess");
    assertThat(report.dslDescriptor()).isNotNull();
    assertThat(report.hasCompensation()).isTrue();
    assertThat(report.executionTrace()).contains("called helper: auditLog");
    assertThat(report.astTree()).isNotNull();
    assertThat(report.astTree().name()).isEqualTo("ExplainProcess");
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
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainGraphReport> result = explainPipe.execute("MissingProcess",
            contextFactory.of("payload", ExecutionMode.EXPLAIN, "run-fail"));

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport report = result.value();
    assertThat(report.errors()).hasSize(1);
    assertThat(report.errors().get(0).message())
            .contains("No DSL entity registered: MissingProcess");
  }

  private ExplainDslPipe newPipe(ExternalCallRecorder recorder) {
    return new ExplainDslPipe(recorder, contextFactory, dryRunLoggingContext, bufferRegistry,
            defaultMaxEventsPerRun(), previewProperties,
            new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()),
            new SimpleMeterRegistry(), new ExplainDiagramRenderer(),
            new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096), null);
  }
}
