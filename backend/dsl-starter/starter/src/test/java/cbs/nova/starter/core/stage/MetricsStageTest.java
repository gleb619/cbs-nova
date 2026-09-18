package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.HierarchyAccumulator;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MetricsStageTest {

  @Test
  void runModeSkipsCollectionAndPassesThroughUntouched() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-1").build(),
            ExecutionMode.RUN,
            "run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new MetricsStage(registry).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("downstream");
    assertThat(pipeContext.getAttribute("metrics", PreviewMetricsSnapshot.class)).isNull();
    assertThat(registry.find(StarterConstants.CALL_COUNTER).counters()).isEmpty();
    assertThat(registry.find(StarterConstants.DURATION_TIMER).timers()).isEmpty();
  }

  @Test
  void previewModeRecordsCallCountsFromAstTreeAndRegistersMeters() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW,
            "run-1");
    CallNode root = CallNode.node("root", CallKind.PROCESS, null, "out", true,
            List.of(
                    CallNode.leaf("tx1", CallKind.TRANSACTION, null, "ok", true),
                    CallNode.leaf("helper1", CallKind.HELPER, null, "ok", true)),
            List.of());
    pipeContext.setAttribute("astTree", root);

    DslPipeStage.Next next = c -> Result.success("downstream");
    new MetricsStage(registry).execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
            "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);
    assertThat(snapshot.callCounts()).containsEntry(CallKind.TRANSACTION, 1);
    assertThat(snapshot.callCounts()).containsEntry(CallKind.HELPER, 1);

    Counter process = registry.get(StarterConstants.CALL_COUNTER).tag("kind", "PROCESS").counter();
    Counter transaction = registry.get(StarterConstants.CALL_COUNTER).tag("kind", "TRANSACTION")
            .counter();
    Counter helper = registry.get(StarterConstants.CALL_COUNTER).tag("kind", "HELPER").counter();
    assertThat(process.count()).isEqualTo(1.0);
    assertThat(transaction.count()).isEqualTo(1.0);
    assertThat(helper.count()).isEqualTo(1.0);

    Timer timer = registry.get(StarterConstants.DURATION_TIMER)
            .tag("mode", "PREVIEW")
            .tag("process", "Ping")
            .timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
            .isGreaterThanOrEqualTo(0.0);
  }

  @Test
  void previewModeRecordsExternalCallTypesAndRegistersCounters() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW,
            "run-1");
    List<ExternalCall> calls = List.of(
            new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L, Map.of()),
            new ExternalCall(ExternalCallRecorder.TYPE_HTTP, "http://x", "GET", 0L, Map.of()),
            new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "insert", 0L,
                    Map.of()));
    pipeContext.setAttribute("externalCalls", calls);

    DslPipeStage.Next next = c -> Result.success("downstream");
    new MetricsStage(registry).execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
            "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.externalCallCounts())
            .containsEntry(ExternalCallRecorder.TYPE_DATABASE, 2)
            .containsEntry(ExternalCallRecorder.TYPE_HTTP, 1);

    Counter database = registry.get(StarterConstants.EXTERNAL_CALL_COUNTER)
            .tag("type", ExternalCallRecorder.TYPE_DATABASE).counter();
    Counter http = registry.get(StarterConstants.EXTERNAL_CALL_COUNTER)
            .tag("type", ExternalCallRecorder.TYPE_HTTP).counter();
    assertThat(database.count()).isEqualTo(2.0);
    assertThat(http.count()).isEqualTo(1.0);
  }

  @Test
  void metricsAttributeSetEvenWhenProceedThrows() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW,
            "run-1");
    pipeContext.setAttribute("astTree",
            CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));

    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new MetricsStage(registry).execute(pipeContext, next))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("downstream boom");

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
            "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);

    Timer timer = registry.get(StarterConstants.DURATION_TIMER)
            .tag("mode", "PREVIEW")
            .tag("process", "Ping")
            .timer();
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  void missingAstTreeAndExternalCallsAttributesDoNotThrow() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW,
            "run-1");

    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new MetricsStage(registry).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
            "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).isEmpty();
    assertThat(snapshot.externalCallCounts()).isEmpty();
    assertThat(registry.find(StarterConstants.CALL_COUNTER).counters()).isEmpty();
    assertThat(registry.find(StarterConstants.EXTERNAL_CALL_COUNTER).counters()).isEmpty();
    assertThat(registry.find(StarterConstants.DURATION_TIMER).timer()).isNotNull();
  }

  @Test
  void explainModeReadsAstTreeAndExternalCallsFromAccumulatorAndWritesMetricsBack() {
    MeterRegistry registry = new SimpleMeterRegistry();
    HierarchyAccumulator accumulator = new HierarchyAccumulator();
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.EXPLAIN)
            .runId("run-1").build()
            .withMetadata(Constants.HIERARCHY_GRAPH_ACCUMULATOR_KEY, accumulator);
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.EXPLAIN, "run-1");
    accumulator.astTree(CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));
    accumulator.externalCalls(List.of(
            Map.of("type", ExternalCallRecorder.TYPE_DATABASE),
            Map.of("type", ExternalCallRecorder.TYPE_HTTP)));

    DslPipeStage.Next next = c -> Result.success("downstream");
    new MetricsStage(registry).execute(pipeContext, next);

    assertThat(accumulator.metrics()).isNotNull();
    assertThat(accumulator.metrics().callCounts()).containsEntry(CallKind.PROCESS, 1);
    assertThat(accumulator.metrics().externalCallCounts())
            .containsEntry(ExternalCallRecorder.TYPE_DATABASE, 1)
            .containsEntry(ExternalCallRecorder.TYPE_HTTP, 1);
    assertThat(pipeContext.getAttribute("metrics", PreviewMetricsSnapshot.class)).isNull();
  }

  @Test
  void explainModeAlsoCollectsMetricsAndTagsTimerWithExplain() {
    MeterRegistry registry = new SimpleMeterRegistry();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.EXPLAIN).runId("run-1").build(),
            ExecutionMode.EXPLAIN,
            "run-1");
    pipeContext.setAttribute("astTree",
            CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));

    DslPipeStage.Next next = c -> Result.success("downstream");

    new MetricsStage(registry).execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
            "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);

    Timer timer = registry.get(StarterConstants.DURATION_TIMER)
            .tag("mode", "EXPLAIN")
            .tag("process", "Ping")
            .timer();
    assertThat(timer.count()).isEqualTo(1L);
  }
}
