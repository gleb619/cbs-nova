package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainDslPipeTest {

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
  void explainReturnsLightweightReportWithMermaidAndChildren() {
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

    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainReport> result = explainPipe.execute("ExplainProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-explain")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = result.value();
    assertThat(report.name()).isEqualTo("ExplainProcess");
    assertThat(report.description()).isEqualTo("Process: ExplainProcess");
    assertThat(report.mermaid()).isNotBlank();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void failedRunStillReturnsReportForMissingEntity() {
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainReport> result = explainPipe.execute("MissingProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-fail").build());

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = result.value();
    assertThat(report.name()).isEqualTo("MissingProcess");
    assertThat(report.mermaid()).isNotBlank();
    assertThat(report.children()).isEmpty();
  }

  private ExplainDslPipe newPipe(ExternalCallRecorder recorder) {
    HierarchyDslPipe hierarchyDslPipe = new HierarchyDslPipe(recorder, dryRunLoggingContext,
            bufferRegistry, defaultMaxEventsPerRun(), previewProperties,
            new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()),
            new SimpleMeterRegistry(), new cbs.nova.starter.reporting.HierarchyDiagramRenderer(),
            null);
    return new ExplainDslPipe(hierarchyDslPipe,
            new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096));
  }
}
