package cbs.nova.starter.preview;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.ExternalCallTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

class TemporalActivityCallCaptureInterceptorTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private ExternalCallTracker tracker;
  private ArrayList<ExternalCallTracker.CallDetail> recorded;
  private TransactionInvoker delegate;
  private TemporalActivityCallCaptureInterceptor interceptor;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    recorded = new ArrayList<>();
    delegate = (name, input, ctx) -> Result.success("ok");
    interceptor = new TemporalActivityCallCaptureInterceptor(delegate, tracker);
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.resetGlobalCounts();
  }

  @Test
  void recordsActivityCallBeforeDelegating() {
    var input = Map.of("key", "value");
    Context<?> ctx = contextFactory.of(input, ExecutionMode.PREVIEW, "run-123");

    tracker.startTracking(recorded);
    Result<?> result = interceptor.invoke("MyTx", input, ctx);
    tracker.stopTracking();

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok");
    assertThat(recorded).hasSize(1);

    var call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallTracker.TYPE_ACTIVITY);
    assertThat(call.target()).isEqualTo("MyTx");
    assertThat(call.operation()).isEqualTo("execute");

    var payload = (Map<String, Object>) call.metadata().get("payload");
    assertThat(payload).containsEntry("runId", "run-123");
    assertThat(payload).containsEntry("mode", ExecutionMode.PREVIEW);
    assertThat(payload).containsEntry("input", input);
  }

  @Test
  void recordsActivityCallWhenDelegateReturnsFailure() {
    var error = new RuntimeException("boom");
    delegate = (name, input, ctx) -> Result.failure(error);
    interceptor = new TemporalActivityCallCaptureInterceptor(delegate, tracker);

    Context<?> ctx = contextFactory.of("body", ExecutionMode.EXPLAIN, "run-456");

    tracker.startTracking(recorded);
    Result<?> result = interceptor.invoke("BadTx", "body", ctx);
    tracker.stopTracking();

    assertThat(!result.isSuccess()).isTrue();
    assertThat(recorded).hasSize(1);

    var call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallTracker.TYPE_ACTIVITY);
    assertThat(call.target()).isEqualTo("BadTx");
    assertThat(call.operation()).isEqualTo("execute");

    assertThat(tracker.getGlobalCounts()).containsEntry(ExternalCallTracker.TYPE_ACTIVITY, 1);
  }
}
