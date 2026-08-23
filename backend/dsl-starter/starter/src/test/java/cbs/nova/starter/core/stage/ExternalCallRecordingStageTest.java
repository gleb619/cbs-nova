package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ExternalCallRecordingStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void startRunCalledBeforeProceedAndFinishRunAfter() {
    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    DslPipeContext pipeContext = newPipeContext("run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    new ExternalCallRecordingStage(recorder).execute(pipeContext, next);

    InOrder order = inOrder(recorder);
    order.verify(recorder).startRun("run-1");
    order.verify(recorder).finishRun("run-1");
  }

  @Test
  void externalCallsAttributeSetFromFinishRunReturnValue() {
    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    List<ExternalCall> recorded = List.of(
        new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L, Map.of()),
        new ExternalCall(ExternalCallRecorder.TYPE_HTTP, "http://x", "GET", 0L, Map.of())
    );
    when(recorder.finishRun("run-2")).thenReturn(recorded);

    DslPipeContext pipeContext = newPipeContext("run-2");
    DslPipeStage.Next next = c -> Result.success("downstream");

    new ExternalCallRecordingStage(recorder).execute(pipeContext, next);

    @SuppressWarnings("unchecked")
    List<ExternalCall> attributeCalls =
        (List<ExternalCall>) pipeContext.getAttribute("externalCalls");
    assertThat(attributeCalls).hasSize(2);
    assertThat(attributeCalls.get(0).type()).isEqualTo(ExternalCallRecorder.TYPE_DATABASE);
    assertThat(attributeCalls.get(1).type()).isEqualTo(ExternalCallRecorder.TYPE_HTTP);
  }

  @Test
  void externalCallsAttributeSetEvenWhenProceedThrows() {
    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    List<ExternalCall> recorded = List.of(
        new ExternalCall(ExternalCallRecorder.TYPE_DATABASE, "jdbc:db", "select", 0L, Map.of())
    );
    when(recorder.finishRun("run-3")).thenReturn(recorded);

    DslPipeContext pipeContext = newPipeContext("run-3");
    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new ExternalCallRecordingStage(recorder)
            .execute(pipeContext, next))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("downstream boom");

    @SuppressWarnings("unchecked")
    List<ExternalCall> attributeCalls =
        (List<ExternalCall>) pipeContext.getAttribute("externalCalls");
    assertThat(attributeCalls).isNotNull();
    assertThat(attributeCalls).hasSize(1);
  }

  @Test
  void emptyRecordedCallsFlowThroughUnchanged() {
    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    when(recorder.finishRun("run-4")).thenReturn(List.of());

    DslPipeContext pipeContext = newPipeContext("run-4");
    DslPipeStage.Next next = c -> Result.success("downstream");

    new ExternalCallRecordingStage(recorder).execute(pipeContext, next);

    @SuppressWarnings("unchecked")
    List<ExternalCall> attributeCalls =
        (List<ExternalCall>) pipeContext.getAttribute("externalCalls");
    assertThat(attributeCalls).isEmpty();
    verify(recorder).startRun("run-4");
    verify(recorder).finishRun("run-4");
  }

  @Test
  void downstreamResultIsReturnedOnSuccess() {
    ExternalCallRecorder recorder = mock(ExternalCallRecorder.class);
    when(recorder.finishRun("run-5")).thenReturn(List.of());

    DslPipeContext pipeContext = newPipeContext("run-5");
    DslPipeStage.Next next = c -> Result.success("downstream-value");

    Result<?> result = new ExternalCallRecordingStage(recorder).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("downstream-value");
  }

  private DslPipeContext newPipeContext(String runId) {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.PREVIEW, runId);
    return new DslPipeContext("Ping", ctx, ExecutionMode.PREVIEW, runId);
  }
}
