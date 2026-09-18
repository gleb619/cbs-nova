package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.HierarchyAccumulator;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionTraceStageTest {

  @Test
  void proceedReceivesWrappedContextWithFreshExecutionTraceCollector() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW)
            .runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of(
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
    assertThat(wrapped.dslContext()).isNotSameAs(originalDsl);

    ExecutionTraceCollector collector = wrapped.dslContext().executionTraceCollector();
    assertThat(collector).isNotNull();
  }

  @Test
  void executionTraceAttributeIsSetFromCollectorSnapshot() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW)
            .runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    DslPipeStage.Next next = c -> {
      ExecutionTraceCollector inside = c.dslContext().executionTraceCollector();
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
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW)
            .runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of(
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
  void executionTraceGoesToAccumulatorWhenPresent() {
    HierarchyAccumulator accumulator = new HierarchyAccumulator();
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.EXPLAIN)
            .runId("run-1").build()
            .withMetadata(Constants.HIERARCHY_GRAPH_ACCUMULATOR_KEY, accumulator);
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.EXPLAIN, "run-1");

    DslPipeStage.Next next = c -> {
      c.dslContext().executionTraceCollector().add("first-step");
      return Result.success("downstream");
    };

    new ExecutionTraceStage().execute(pipeContext, next);

    assertThat(accumulator.executionTrace()).containsExactly("first-step");
    assertThat(pipeContext.getAttribute("executionTrace", List.class)).isNull();
  }

  @Test
  void collectorStartEnablesAddAndStopClearsEntries() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW)
            .runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      ExecutionTraceCollector inside = c.dslContext().executionTraceCollector();
      inside.add("while-running");
      return Result.success("ok");
    };

    new ExecutionTraceStage().execute(pipeContext, next);

    ExecutionTraceCollector capturedCollector = captured.get().dslContext()
            .executionTraceCollector();
    assertThat(capturedCollector.snapshot()).isEmpty();

    @SuppressWarnings("unchecked")
    List<String> trace = (List<String>) pipeContext.getAttribute("executionTrace", List.class);
    assertThat(trace).containsExactly("while-running");
  }
}
