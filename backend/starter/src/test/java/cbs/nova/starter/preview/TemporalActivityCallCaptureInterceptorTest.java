package cbs.nova.starter.preview;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TemporalActivityCallCaptureInterceptorTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private RunScopedExternalCallRecorder recorder;
  private List<ExternalCall> recorded;
  private TransactionInvoker delegate;
  private TemporalActivityCallCaptureInterceptor interceptor;

  @BeforeEach
  void setUp() {
    recorder = new RunScopedExternalCallRecorder(null);
    recorder.resetGlobalCounts();
    recorded = new ArrayList<>();
    delegate = (name, input, ctx) -> Result.success("ok");
    interceptor = new TemporalActivityCallCaptureInterceptor(delegate, recorder);
  }

  @AfterEach
  void tearDown() {
    recorder.resetGlobalCounts();
  }

  @Test
  void recordsActivityCallBeforeDelegating() {
    var input = Map.of("key", "value");
    Context<?> ctx = contextFactory.of(input, ExecutionMode.PREVIEW, "run-123");

    recorder.startRun("run-123");
    Result<?> result = interceptor.invoke("MyTx", input, ctx);
    recorded.addAll(recorder.finishRun("run-123"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok");
    assertThat(recorded).hasSize(1);

    var call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallRecorder.TYPE_ACTIVITY);
    assertThat(call.target()).isEqualTo("MyTx");
    assertThat(call.operation()).isEqualTo("execute");

    var payload = (Map<String, Object>) call.metadata().get("payload");
    assertThat(payload).containsEntry("runId", "run-123");
    assertThat(payload).containsEntry("mode", ExecutionMode.PREVIEW);
    assertThat(payload).containsEntry("input", input);
  }

  @Test
  void delegatesWhenNoMockIsConfigured() {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.PREVIEW, "run-000");

    recorder.startRun("run-000");
    Result<?> result = interceptor.invoke("MyTx", "body", ctx);
    recorded.addAll(recorder.finishRun("run-000"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok");
  }
}
