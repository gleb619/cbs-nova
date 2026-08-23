package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.metrics.PreviewMetricsCollector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MetricsStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @AfterEach
  void cleanup() {
    PreviewMetricsCollector.remove();
    PreviewMetricsCollector.resetLatestSnapshot();
  }

  @Test
  void runModeSkipsCollectionAndPassesThroughUntouched() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.RUN, "run-1"),
        ExecutionMode.RUN,
        "run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new MetricsStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("downstream");
    assertThat(pipeContext.getAttribute("metrics", PreviewMetricsSnapshot.class)).isNull();
  }

  @Test
  void previewModeRecordsCallCountsFromAstTree() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.PREVIEW, "run-1"),
        ExecutionMode.PREVIEW,
        "run-1");
    CallNode root = CallNode.node("root", CallKind.PROCESS, null, "out", true,
        List.of(
            CallNode.leaf("tx1", CallKind.TRANSACTION, null, "ok", true),
            CallNode.leaf("helper1", CallKind.HELPER, null, "ok", true)
        ),
        List.of());
    pipeContext.setAttribute("astTree", root);

    DslPipeStage.Next next = c -> Result.success("downstream");
    new MetricsStage().execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
        "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);
    assertThat(snapshot.callCounts()).containsEntry(CallKind.TRANSACTION, 1);
    assertThat(snapshot.callCounts()).containsEntry(CallKind.HELPER, 1);
  }

  @Test
  void previewModeRecordsExternalCallTypes() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.PREVIEW, "run-1"),
        ExecutionMode.PREVIEW,
        "run-1");
    List<ExternalCall> calls = List.of(
        new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L, Map.of()),
        new ExternalCall(ExternalCallRecorder.TYPE_HTTP, "http://x", "GET", 0L, Map.of()),
        new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "insert", 0L, Map.of())
    );
    pipeContext.setAttribute("externalCalls", calls);

    DslPipeStage.Next next = c -> Result.success("downstream");
    new MetricsStage().execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
        "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.externalCallCounts())
        .containsEntry(ExternalCallRecorder.TYPE_DATABASE, 2)
        .containsEntry(ExternalCallRecorder.TYPE_HTTP, 1);
  }

  @Test
  void metricsAttributeSetEvenWhenProceedThrows() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.PREVIEW, "run-1"),
        ExecutionMode.PREVIEW,
        "run-1");
    pipeContext.setAttribute("astTree",
        CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));

    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new MetricsStage().execute(pipeContext, next))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("downstream boom");

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
        "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);
  }

  @Test
  void missingAstTreeAndExternalCallsAttributesDoNotThrow() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.PREVIEW, "run-1"),
        ExecutionMode.PREVIEW,
        "run-1");

    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new MetricsStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
        "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).isEmpty();
    assertThat(snapshot.externalCallCounts()).isEmpty();
  }

  @Test
  void explainModeAlsoCollectsMetrics() {
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping",
        contextFactory.of("body", ExecutionMode.EXPLAIN, "run-1"),
        ExecutionMode.EXPLAIN,
        "run-1");
    pipeContext.setAttribute("astTree",
        CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));

    DslPipeStage.Next next = c -> Result.success("downstream");

    new MetricsStage().execute(pipeContext, next);

    PreviewMetricsSnapshot snapshot = pipeContext.getAttribute(
        "metrics", PreviewMetricsSnapshot.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.callCounts()).containsEntry(CallKind.PROCESS, 1);
  }
}
