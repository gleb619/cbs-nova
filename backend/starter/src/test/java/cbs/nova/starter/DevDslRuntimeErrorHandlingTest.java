package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DevDslRuntimeErrorHandlingTest {

  private static final String MISSING_HELPER = "MissingHelper";

  private final RunScopedExternalCallRecorder recorder = new RunScopedExternalCallRecorder(null);
  private final ExecutionTraceCollector traceCollector = DslConfig.dslConfig()
          .executionTraceCollector();
  private final ContextFactory contextFactory = new ContextFactory();
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(dryRunLoggingContext,
          1000);
  private Appender<ILoggingEvent> originalDryRunAppender;
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null);
  private final PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
          dryRunLoggingContext, null, previewProperties, traceCollector);
  private final RunDslPipe runPipe = new RunDslPipe(contextFactory, traceCollector);
  private final ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
          dryRunLoggingContext, previewProperties, traceCollector);
  private final DevDslRuntime runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    // Register a process that intentionally references a non-existent helper.
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

    PreviewErrorDetail firstError = report.errors().get(0);
    assertThat(firstError.code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND);
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
}
