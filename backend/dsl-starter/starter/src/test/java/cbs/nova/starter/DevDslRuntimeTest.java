package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

class DevDslRuntimeTest {

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
          new RunScopedFakeConfig());
  private final RunDslPipe runPipe = new RunDslPipe(contextFactory, recorder,
          new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
  private final ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
          previewProperties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
  private final DevDslRuntime runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

  @BeforeEach
  void reset() {
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
  void previewDispatchesToProcess() {
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    var result = runtime.preview("Ping", ctx);
    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = result.value();
    assertThat(report.name()).isEqualTo("Ping");
    assertThat(report.mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isEqualTo("pong");
    assertThat(report.executionTrace()).isNotNull();
    assertThat(report.astTree()).isNotNull();
    assertThat(report.astTree().name()).isEqualTo("Ping");
    assertThat(report.astTree().kind()).isEqualTo(CallKind.PROCESS);
    assertThat(report.dryRunLogs()).isNotNull();
  }

  @Test
  void runDispatchesToProcess() {
    var ctx = contextFactory.of("input", ExecutionMode.RUN);
    var result = runtime.run("Ping", ctx);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void explainReturnsReport() {
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("Ping", ctx);
    var renderer = new ExplainDiagramRenderer();
    assertThat(report.name()).isEqualTo("Ping");
    assertThat(report.description()).isEqualTo("Process: Ping");
    assertThat(renderer.mermaidDiagram(report)).isNotBlank();
    assertThat(report.executionTrace()).isNotNull();
    assertThat(report.astTree()).isNotNull();
    assertThat(report.astTree().name()).isEqualTo("Ping");
    assertThat(report.astTree().kind()).isEqualTo(CallKind.PROCESS);
    assertThat(report.dryRunLogs()).isNotNull();
  }

  @Test
  void previewAstTreeContainsNestedTransaction() {
    GlobalManager.globalManager()
            .registerTransaction(Dsl.transaction("InnerTx")
                    .execute(ctx -> Result.success("tx-out")).build());
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Outer")
                    .execute(ctx -> ctx.runTransaction("InnerTx", ctx.body())).build());

    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW);
    var result = runtime.preview("Outer", ctx);

    assertThat(result.isSuccess()).isTrue();
    var tree = result.value().astTree();
    assertThat(tree).isNotNull();
    assertThat(tree.name()).isEqualTo("Outer");
    assertThat(tree.kind()).isEqualTo(CallKind.PROCESS);
    assertThat(tree.children()).hasSize(1);
    assertThat(tree.children().get(0).name()).isEqualTo("InnerTx");
    assertThat(tree.children().get(0).kind()).isEqualTo(CallKind.TRANSACTION);
  }

  @Test
  void explainDescriptionReflectsEntityKind() {
    GlobalManager.globalManager()
            .registerTransaction(Dsl.transaction("EchoTx")
                    .execute(ctx -> Result.success("echo")).build());

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var processReport = runtime.explain("Ping", ctx);
    var transactionReport = runtime.explain("EchoTx", ctx);

    assertThat(processReport.description()).isEqualTo("Process: Ping");
    assertThat(transactionReport.description()).isEqualTo("Transaction: EchoTx");
  }

  @Test
  void explainTraceContainsSteps() {
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("Ping", ctx);
    assertThat(report.executionTrace()).isNotNull();
  }

  @Test
  void unknownEntityReturnsFailure() {
    var ctx = contextFactory.of("x", ExecutionMode.PREVIEW);
    var result = runtime.preview("Unknown", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().success()).isFalse();
    assertThat(result.value().errors()).isNotEmpty();
    assertThat(result.value().errors().get(0).code().name()).isEqualTo("HELPER_NOT_FOUND");
  }

  @Test
  void explainTracksExternalCallsAndDiagrams() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("TrackedProcess")
                    .execute(ctx -> {
                      recorder.record("jdbc", "user-db", "SELECT * FROM users", null);
                      recorder.record("http", "payment-api", "POST /pay",
                              "{\"amount\": 100}");
                      return Result.success("ok");
                    }).build());

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("TrackedProcess", ctx);
    var renderer = new ExplainDiagramRenderer();

    assertThat(report.name()).isEqualTo("TrackedProcess");
    assertThat(renderer.plantUmlDiagram(report)).contains("TrackedProcess");
    assertThat(renderer.bpmnXml(report)).contains("bpmn:process");
    assertThat(report.callCounts()).containsEntry("database", 1);
    assertThat(report.callCounts()).containsEntry("http", 1);
    assertThat(report.externalCalls()).hasSize(2);
    assertThat(report.externalCalls().get(0)).containsEntry("type", "database");
    assertThat(report.externalCalls().get(0)).containsEntry("target", "user-db");
    assertThat(report.externalCalls().get(0)).containsEntry("operation", "SELECT * FROM users");
  }

  @Test
  void recorderTriggersListeners() {
    var calls = new ArrayList<String>();
    recorder.registerListener(event -> {
      if (event instanceof DslExternalCallEvent e) {
        calls.add(e.type() + ":" + e.target());
      }
    });

    recorder.record("mq", "queue-1", "send", "msg");
    assertThat(calls).containsExactly("mq:queue-1");
  }
}
