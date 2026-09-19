package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.event.DslExecutionEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.listener.DslExecutionListener;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DslExecutionEventStageTest {

  private final List<DslExecutionEvent> captured = new ArrayList<>();

  private final DslExecutionEventBus eventBus;
  private final DslExecutionEventStage stage;
  private final DslPipeContext pipeContext;

  DslExecutionEventStageTest() {
    this.eventBus = new DslExecutionEventBus();
    eventBus.register(captured::add);
    this.stage = new DslExecutionEventStage(eventBus);
    this.pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-1").build(),
            ExecutionMode.RUN,
            "run-1");
  }

  @Test
  void successPublishesStartedThenCompletedAndReturnsDownstreamResult() {
    Result<?> success = Result.success("pong");
    DslPipeStage.Next next = c -> success;

    Result<?> stageResult = stage.execute(pipeContext, next);

    assertThat(stageResult).isSameAs(success);
    assertThat(captured).hasSize(2);
    assertThat(captured.get(0))
            .isInstanceOfSatisfying(DslRunStartedEvent.class, started -> {
              assertThat(started.runId()).isEqualTo("run-1");
              assertThat(started.name()).isEqualTo("Ping");
              assertThat(started.mode()).isEqualTo(ExecutionMode.RUN);
            });
    assertThat(captured.get(1))
            .isInstanceOfSatisfying(DslRunCompletedEvent.class, completed -> {
              assertThat(completed.runId()).isEqualTo("run-1");
              assertThat(completed.name()).isEqualTo("Ping");
              assertThat(completed.mode()).isEqualTo(ExecutionMode.RUN);
              assertThat(completed.result()).isSameAs(success);
            });
  }

  @Test
  void downstreamFailurePublishesCompletedWithFailureResultAndReturnsSameResult() {
    Result<?> failure = Result.failure(new RuntimeException("boom"));
    DslPipeStage.Next next = c -> failure;

    Result<?> stageResult = stage.execute(pipeContext, next);

    assertThat(stageResult).isSameAs(failure);
    assertThat(captured).hasSize(2);
    assertThat(captured.get(1))
            .isInstanceOfSatisfying(DslRunCompletedEvent.class,
                    completed -> assertThat(completed.result()).isInstanceOf(Result.Failure.class));
  }

  @Test
  void thrownDownstreamExceptionRePublishedCompletedWithFailureAndReThrown() {
    DslPipeStage.Next next = c -> {
      throw new RuntimeException("boom");
    };

    assertThatThrownBy(() -> stage.execute(pipeContext, next))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");

    assertThat(captured).hasSize(2);
    assertThat(captured.get(1))
            .isInstanceOfSatisfying(DslRunCompletedEvent.class,
                    completed -> assertThat(completed.result()).isInstanceOf(Result.Failure.class));
  }

  @Test
  void throwingListenerDoesNotEscapeExecuteAndStageStillReturnsDownstream() {
    DslExecutionEventBus throwingBus = new DslExecutionEventBus();
    DslExecutionListener throwing = event -> {
      throw new IllegalStateException("listener boom");
    };
    throwingBus.register(throwing);
    DslExecutionEventStage throwingStage = new DslExecutionEventStage(throwingBus);
    Result<?> success = Result.success("pong");
    DslPipeStage.Next next = c -> success;

    Result<?> stageResult = throwingStage.execute(pipeContext, next);

    assertThat(stageResult).isSameAs(success);
  }

  @Test
  void correlationIdFromContextMetadataIsPropagatedToEvents() {
    SimpleContext ctx = SimpleContext.builder("body")
            .mode(ExecutionMode.RUN)
            .runId("run-corr")
            .metadata(Map.of(StarterConstants.CORRELATION_ID_METADATA_KEY, "corr-123"))
            .build();
    DslPipeContext context = DslPipeContext.of("Ping", ctx, ExecutionMode.RUN, "run-corr");

    stage.execute(context, c -> Result.success("ok"));

    assertThat(captured).hasSize(2);
    assertThat(captured.get(0))
            .isInstanceOfSatisfying(DslRunStartedEvent.class,
                    started -> assertThat(started.correlationId()).isEqualTo("corr-123"));
    assertThat(captured.get(1))
            .isInstanceOfSatisfying(DslRunCompletedEvent.class,
                    completed -> assertThat(completed.correlationId()).isEqualTo("corr-123"));
  }
}
