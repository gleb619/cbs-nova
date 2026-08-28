package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DevDslRuntimeMetricsTest {

  private final RunScopedExternalCallRecorder recorder = new RunScopedExternalCallRecorder(null);
  private final ContextFactory contextFactory = new ContextFactory();
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry();
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(dryRunLoggingContext,
          bufferRegistry);
  private Appender<ILoggingEvent> originalDryRunAppender;
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null);
  private final PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
          null, previewProperties, new CbsNovaFakesProperties(false, null),
          new RunScopedFakeConfig(), new SimpleMeterRegistry());
  private final RunDslPipe runPipe = new RunDslPipe(contextFactory, recorder,
          new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
  private final ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
          previewProperties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(),
          new SimpleMeterRegistry(), new ExplainDiagramRenderer());
  private final DevDslRuntime runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Ping")
                    .execute(ctx -> Result.success("pong")).build());

    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    originalDryRunAppender = root.getAppender("DRY_RUN");
    if (originalDryRunAppender != null) {
      root.detachAppender(originalDryRunAppender);
    }
    appender.setContext(root.getLoggerContext());
    appender.setName("DRY_RUN");
    appender.start();
    root.addAppender(appender);
    root.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    dryRunLoggingContext.clearRunId();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(appender);
    appender.stop();
    if (originalDryRunAppender != null) {
      root.addAppender(originalDryRunAppender);
    }
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewReportContainsMetrics() {
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    var result = runtime.preview("Ping", ctx);
    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = result.value();
    assertThat(report.metrics()).isNotNull();
    assertThat(report.metrics().executionDurationMs()).isNotNegative();
    assertThat(report.metrics().memoryUsedBytes()).isNotNegative();
    assertThat(report.metrics().callCounts()).containsKey(CallKind.PROCESS);
    assertThat(report.metrics().callCounts().get(CallKind.PROCESS)).isPositive();
  }

  @Test
  void previewMetricsContainsCallKindsFromTree() {
    GlobalManager.globalManager()
            .registerTransaction(Dsl.transaction("InnerTx")
                    .execute(ctx -> Result.success("tx-out")).build());
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Outer")
                    .execute(ctx -> ctx.runTransaction("InnerTx", ctx.body())).build());

    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW);
    var result = runtime.preview("Outer", ctx);
    assertThat(result.isSuccess()).isTrue();
    var metrics = result.value().metrics();
    assertThat(metrics).isNotNull();
    assertThat(metrics.callCounts()).containsEntry(CallKind.PROCESS, 1);
    assertThat(metrics.callCounts()).containsEntry(CallKind.TRANSACTION, 1);
  }

  @Test
  void explainReportContainsMetrics() {
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("Ping", ctx);
    assertThat(report.metrics()).isNotNull();
    assertThat(report.metrics().executionDurationMs()).isNotNegative();
    assertThat(report.metrics().callCounts()).containsKey(CallKind.PROCESS);
  }

  @Test
  void runModeMetricsAreNull() {
    var ctx = contextFactory.of("input", ExecutionMode.RUN);
    var result = runtime.run("Ping", ctx);
    assertThat(result.isSuccess()).isTrue();
  }
}
