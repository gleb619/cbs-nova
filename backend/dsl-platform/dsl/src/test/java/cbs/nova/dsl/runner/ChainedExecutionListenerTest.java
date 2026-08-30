package cbs.nova.dsl.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class ChainedExecutionListenerTest {

  static class RecordingListener implements ExecutionListener {

    final List<String> events = new ArrayList<>();

    @Override
    public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      events.add("onTransactionSuccess:" + execution.transactionName());
    }

    @Override
    public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
            @NonNull Throwable cause) {
      events.add("onTransactionFailure:" + transactionName + ":" + cause.getMessage());
    }

    @Override
    public void onProcessStart(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object input) {
      events.add("onProcessStart:" + name);
    }

    @Override
    public void onProcessEnd(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object output, boolean success) {
      events.add("onProcessEnd:" + name + ":" + success);
    }

    @Override
    public void onTransactionStart(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object input) {
      events.add("onTransactionStart:" + name);
    }

    @Override
    public void onTransactionEnd(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object output, boolean success) {
      events.add("onTransactionEnd:" + name + ":" + success);
    }

    @Override
    public void onHelperStart(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object input) {
      events.add("onHelperStart:" + name);
    }

    @Override
    public void onHelperEnd(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object output, boolean success) {
      events.add("onHelperEnd:" + name + ":" + success);
    }

    @Override
    public void onFunctionStart(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object input) {
      events.add("onFunctionStart:" + name);
    }

    @Override
    public void onFunctionEnd(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object output, boolean success) {
      events.add("onFunctionEnd:" + name + ":" + success);
    }
  }

  /**
   * A listener that records everything normally but throws on a configurable hook. Used to pin down
   * how {@link ChainedExecutionListener} behaves when a delegate throws on a fan-out event.
   */
  static final class ThrowingListener extends RecordingListener {

    private final String throwingHook;
    private final RuntimeException toThrow;

    ThrowingListener(String throwingHook, RuntimeException toThrow) {
      this.throwingHook = throwingHook;
      this.toThrow = toThrow;
    }

    @Override
    public void onProcessStart(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object input) {
      if ("onProcessStart".equals(throwingHook)) {
        throw toThrow;
      }
      super.onProcessStart(runId, name, input);
    }

    @Override
    public void onTransactionEnd(@NonNull String runId, @NonNull String name,
            @org.jspecify.annotations.Nullable Object output, boolean success) {
      if ("onTransactionEnd".equals(throwingHook)) {
        throw toThrow;
      }
      super.onTransactionEnd(runId, name, output, success);
    }
  }

  private static TransactionExecution tx(String name) {
    return new TransactionExecution("r", name, "in", Instant.now());
  }

  @Test
  void onProcessStartFansOutInRegistrationOrder() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onProcessStart("r1", "P", "in");

    assertThat(first.events).containsExactly("onProcessStart:P");
    assertThat(second.events).containsExactly("onProcessStart:P");
  }

  @Test
  void onProcessEndFansOutInRegistrationOrder() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onProcessEnd("r1", "P", "out", true);

    assertThat(first.events).containsExactly("onProcessEnd:P:true");
    assertThat(second.events).containsExactly("onProcessEnd:P:true");
  }

  @Test
  void onTransactionStartAndEndFanOut() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onTransactionStart("r1", "T", "in");
    chained.onTransactionEnd("r1", "T", "out", true);

    assertThat(first.events)
            .containsExactly("onTransactionStart:T", "onTransactionEnd:T:true");
    assertThat(second.events)
            .containsExactly("onTransactionStart:T", "onTransactionEnd:T:true");
  }

  @Test
  void onTransactionSuccessFansOut() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onTransactionSuccess(tx("Tx1"));

    assertThat(first.events).containsExactly("onTransactionSuccess:Tx1");
    assertThat(second.events).containsExactly("onTransactionSuccess:Tx1");
  }

  @Test
  void onTransactionFailureFansOut() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onTransactionFailure("r1", "Tx1", new IllegalStateException("boom"));

    assertThat(first.events).containsExactly("onTransactionFailure:Tx1:boom");
    assertThat(second.events).containsExactly("onTransactionFailure:Tx1:boom");
  }

  @Test
  void onHelperAndFunctionStartEndFanOut() {
    var first = new RecordingListener();
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    chained.onHelperStart("r1", "H", "in");
    chained.onHelperEnd("r1", "H", "out", true);
    chained.onFunctionStart("r1", "F", "in");
    chained.onFunctionEnd("r1", "F", "out", false);

    assertThat(first.events).containsExactly(
            "onHelperStart:H",
            "onHelperEnd:H:true",
            "onFunctionStart:F",
            "onFunctionEnd:F:false");
    assertThat(second.events).containsExactly(
            "onHelperStart:H",
            "onHelperEnd:H:true",
            "onFunctionStart:F",
            "onFunctionEnd:F:false");
  }

  @Test
  void firstDelegateThrowingSkipsSecondAndPropagates() {
    var first = new ThrowingListener("onProcessStart",
            new IllegalStateException("first-delegate-boom"));
    var second = new RecordingListener();
    var chained = new ChainedExecutionListener(first, second);

    assertThatThrownBy(() -> chained.onProcessStart("r1", "P", "in"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("first-delegate-boom");

    assertThat(first.events).isEmpty();
    assertThat(second.events).isEmpty();
  }

  @Test
  void secondDelegateThrowingPropagatesAfterFirstSucceeded() {
    var first = new RecordingListener();
    var second = new ThrowingListener("onTransactionEnd",
            new RuntimeException("second-delegate-boom"));
    var chained = new ChainedExecutionListener(first, second);

    assertThatThrownBy(() -> chained.onTransactionEnd("r1", "T", "out", true))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("second-delegate-boom");

    // First delegate ran fully before second threw on its own callback.
    assertThat(first.events).containsExactly("onTransactionEnd:T:true");
    assertThat(second.events).isEmpty();
  }
}
