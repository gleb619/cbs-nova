package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevDslRuntimeTest {

  private final ExternalCallTracker tracker = new ExternalCallTracker();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private final ContextFactory contextFactory = new ContextFactory();
  private final DevDslRuntime runtime = new DevDslRuntime(tracker, traceCollector, contextFactory);

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Ping")
                    .execute(ctx -> Result.success("pong")).build());
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
    assertThat(report.executionTrace()).isNotEmpty();
    assertThat(report.executionTrace()).contains("started: Ping", "mode: PREVIEW",
            "completed successfully");
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
    assertThat(report.name()).isEqualTo("Ping");
    assertThat(report.description()).contains("Ping");
    assertThat(report.mermaidDiagram()).isNotBlank();
    assertThat(report.executionTrace()).isNotEmpty();
    assertThat(report.executionTrace()).contains("started: Ping");
  }

  @Test
  void explainTraceContainsSteps() {
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("Ping", ctx);
    assertThat(report.executionTrace()).containsExactly(
            "started: Ping",
            "mode: EXPLAIN",
            "result: pong");
  }

  @Test
  void unknownEntityReturnsFailure() {
    var ctx = contextFactory.of("x", ExecutionMode.PREVIEW);
    var result = runtime.preview("Unknown", ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.value()).isNull();
  }

  @Test
  void explainTracksExternalCallsAndDiagrams() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("TrackedProcess")
                    .execute(ctx -> {
                      tracker.record("jdbc", "user-db", "SELECT * FROM users", null);
                      tracker.record("http", "payment-api", "POST /pay",
                              "{\"amount\": 100}");
                      return Result.success("ok");
                    }).build());

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN);
    var report = runtime.explain("TrackedProcess", ctx);

    assertThat(report.name()).isEqualTo("TrackedProcess");
    assertThat(report.plantUmlDiagram()).contains("TrackedProcess");
    assertThat(report.bpmnXml()).contains("bpmn:process");
    assertThat(report.callCounts()).containsEntry("database", 1);
    assertThat(report.callCounts()).containsEntry("http", 1);
    assertThat(report.externalCalls()).hasSize(2);
    assertThat(report.externalCalls().get(0)).containsEntry("type", "database");
    assertThat(report.externalCalls().get(0)).containsEntry("target", "user-db");
    assertThat(report.externalCalls().get(0)).containsEntry("operation", "SELECT * FROM users");
  }

  @Test
  void trackerTriggersListeners() {
    var calls = new ArrayList<String>();
    tracker.registerListener((type, target, op, payload) -> calls.add(type + ":" + target));

    tracker.record("mq", "queue-1", "send", "msg");
    assertThat(calls).containsExactly("mq:queue-1");
  }
}
