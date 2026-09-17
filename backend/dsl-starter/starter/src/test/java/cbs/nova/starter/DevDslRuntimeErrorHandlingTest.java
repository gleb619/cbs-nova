package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import com.github.benmanes.caffeine.cache.Caffeine;
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

class DevDslRuntimeErrorHandlingTest {

  private static int defaultMaxEventsPerRun() {
    return new DryRunProperties(null, null).log().maxEventsPerRun();
  }

  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();

  private static final String MISSING_HELPER = "MissingHelper";

  private final RunIdKeyedExternalCallRecorder recorder = new RunIdKeyedExternalCallRecorder(
          dryRunLoggingContext, null);
  private final ContextFactory contextFactory = new ContextFactory();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry(
          Caffeine.newBuilder().build());
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(dryRunLoggingContext,
          bufferRegistry);
  private Appender<ILoggingEvent> originalDryRunAppender;
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null, null);
  private final PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, defaultMaxEventsPerRun(),
          null, previewProperties, new CbsNovaFakesProperties(false, null),
          new RunScopedFakeConfig(Caffeine.newBuilder().build()), new SimpleMeterRegistry(), null);
  private final RunDslPipe runPipe = new RunDslPipe(contextFactory, recorder,
          new CbsNovaFakesProperties(false, null),
          new RunScopedFakeConfig(Caffeine.newBuilder().build()),
          new DslExecutionEventBus());
  private final ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, defaultMaxEventsPerRun(),
          previewProperties, new CbsNovaFakesProperties(false, null),
          new RunScopedFakeConfig(Caffeine.newBuilder().build()),
          new SimpleMeterRegistry(), new ExplainDiagramRenderer(),
          new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096), null);
  private final DevDslRuntime runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("CallMissing")
                    .execute(ctx -> ctx.runHelper(MISSING_HELPER, ctx.body()))
                    .build());

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
  void previewPopulatesHelperNotFoundWhenReferencingMissingHelper() {
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    Result<PreviewReport> result = runtime.preview("CallMissing", ctx);

    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = result.value();
    assertThat(report).isNotNull();
    assertThat(report.success()).isFalse();
    assertThat(report.output()).isNull();
    assertThat(report.errors()).isNotEmpty();

    ErrorResponse firstError = report.errors().get(0);
    assertThat(firstError.code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND.name());
    assertThat(firstError.context()).containsEntry("name", MISSING_HELPER);
    assertThat(firstError.suggestion()).isNotBlank()
            .containsIgnoringCase("register");
    assertThat(firstError.message()).contains(MISSING_HELPER);
  }

  @Test
  void previewErrorListIsEmptyForSuccessfulProcess() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Ping")
                    .execute(ctx -> Result.success("pong")).build());

    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    Result<PreviewReport> result = runtime.preview("Ping", ctx);

    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = result.value();
    assertThat(report).isNotNull();
    assertThat(report.success()).isTrue();
    assertThat(report.errors()).isNotNull().isEmpty();
  }

  @Test
  void explainReturnsErroredReportWhenPipeFails() {
    GlobalManager.globalManager().resetForTests();

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    ExplainReport report = runtime.explain("Ghost", ctx);

    assertThat(report).isNotNull();
    assertThat(report.name()).isEqualTo("Ghost");
    assertThat(report.description()).contains("Entity: Ghost", "1 errors");
  }
}
