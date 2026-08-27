package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionTraceStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void proceedReceivesWrappedContextWithFreshExecutionTraceCollector() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      return Result.success("downstream");
    };

    new ExecutionTraceStage().execute(pipeContext, next);

    DslPipeContext wrapped = captured.get();
    assertThat(wrapped).isNotNull();
    assertThat(wrapped).isNotSameAs(pipeContext);
    assertThat(wrapped.getDslContext()).isNotSameAs(originalDsl);

    ExecutionTraceCollector collector = wrapped.getDslContext().executionTraceCollector();
    assertThat(collector).isNotNull();
  }

  @Test
  void executionTraceAttributeIsSetFromCollectorSnapshot() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> {
      ExecutionTraceCollector inside = c.getDslContext().executionTraceCollector();
      assertThat(inside).isNotNull();
      inside.add("first-step");
      inside.add("second-step");
      return Result.success("downstream");
    };

    new ExecutionTraceStage().execute(pipeContext, next);

    @SuppressWarnings("unchecked")
    List<String> trace = (List<String>) pipeContext.getAttribute("executionTrace", List.class);
    assertThat(trace).containsExactly("first-step", "second-step");
  }

  @Test
  void executionTraceAttributeIsSetEvenWhenProceedThrows() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new ExecutionTraceStage().execute(pipeContext, next))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("downstream boom");

    @SuppressWarnings("unchecked")
    List<String> trace = (List<String>) pipeContext.getAttribute("executionTrace", List.class);
    assertThat(trace).isNotNull();
    assertThat(trace).isEmpty();
  }

  @Test
  void collectorStartEnablesAddAndStopClearsEntries() {
    Context<?> originalDsl = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    DslPipeContext pipeContext = new DslPipeContext(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      ExecutionTraceCollector inside = c.getDslContext().executionTraceCollector();
      inside.add("while-running");
      return Result.success("ok");
    };

    new ExecutionTraceStage().execute(pipeContext, next);

    ExecutionTraceCollector capturedCollector = captured.get().getDslContext()
            .executionTraceCollector();
    assertThat(capturedCollector.snapshot()).isEmpty();

    @SuppressWarnings("unchecked")
    List<String> trace = (List<String>) pipeContext.getAttribute("executionTrace", List.class);
    assertThat(trace).containsExactly("while-running");
  }
}
