package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionTreeStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void runModePassesThroughOriginalContextUnchanged() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.RUN, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      return Result.success("downstream");
    };

    Result<?> result = new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("downstream");
    assertThat(captured.get()).isSameAs(pipeContext);
    assertThat(pipeContext.getAttribute("astTree", CallNode.class)).isNull();
  }

  @Test
  void previewModeSetsAstTreeAttributeFromCollectorTree() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> {
      ExecutionListener listener = c.getDslContext().executionListener();
      assertThat(listener).isNotNull();
      listener.onProcessStart(c.getRunId(), "Ping", c.getDslContext().body());
      listener.onTransactionStart(c.getRunId(), "tx1", null);
      listener.onTransactionEnd(c.getRunId(), "tx1", "ok", true);
      listener.onProcessEnd(c.getRunId(), "Ping", "done", true);
      return Result.success("downstream");
    };

    new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next);

    CallNode tree = pipeContext.getAttribute("astTree", CallNode.class);
    assertThat(tree).isNotNull();
    assertThat(tree.name()).isEqualTo("Ping");
    assertThat(tree.kind()).isEqualTo(CallKind.PROCESS);
    assertThat(tree.children()).hasSize(1);
    assertThat(tree.children().get(0).name()).isEqualTo("tx1");
    assertThat(tree.children().get(0).kind()).isEqualTo(CallKind.TRANSACTION);
  }

  @Test
  void previewModeSetsAstTreeAttributeToNullWhenCollectorTreeEmpty() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> Result.success("downstream");

    new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next);

    assertThat(pipeContext.getAttribute("astTree", CallNode.class)).isNull();
  }

  @Test
  void previewModeProceedsWithWrappedContext() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      return Result.success("downstream");
    };

    new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next);

    DslPipeContext wrapped = captured.get();
    assertThat(wrapped).isNotSameAs(pipeContext);
    assertThat(wrapped.getDslContext()).isNotSameAs(originalDsl);
    assertThat(wrapped.getDslContext().executionListener()).isNotNull();
    assertThat(wrapped.getDslContext().mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(wrapped.getDslContext().runId()).isEqualTo("run-1");
  }

  @Test
  void astTreeAttributeIsSetEvenWhenProceedThrows() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("downstream boom");

    assertThat(pipeContext.getAttribute("astTree", CallNode.class)).isNull();
  }

  @Test
  void explainModeAlsoBuildsTree() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.EXPLAIN, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
        "Ping", originalDsl, ExecutionMode.EXPLAIN, "run-1");

    DslPipeStage.Next next = c -> Result.success("downstream");

    new ExecutionTreeStage(contextFactory, 32).execute(pipeContext, next);

    pipeContext.getAttribute("astTree", CallNode.class);
  }
}
