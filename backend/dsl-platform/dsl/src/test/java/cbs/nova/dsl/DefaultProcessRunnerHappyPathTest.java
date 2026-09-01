package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.util.ArrayList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Characterization specs for the happy paths of {@link DefaultProcessRunner} that the existing
 * Explain / Compensation / Runner tests do not pin down: the RUN-mode direct execution path, the
 * process-lifecycle listener events emitted to a user-provided listener, and the value/runnable
 * propagation from {@code executeLogic} through to the result.
 */
class DefaultProcessRunnerHappyPathTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          new DefaultCompensationRegistry());

  @Test
  void runModeExecutesLogicAndReturnsItsResult() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ran"))
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-happy");

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ran");
  }

  @Test
  void runModeEmitsProcessStartAndEndEventsToUserListener() {
    var events = new ArrayList<String>();
    var listener = new ExecutionListener() {
      @Override
      public void onProcessStart(@NonNull String runId, @NonNull String name,
              @Nullable Object input) {
        events.add("start:" + name + ":" + input);
      }

      @Override
      public void onProcessEnd(@NonNull String runId, @NonNull String name,
              @Nullable Object output, boolean success) {
        events.add("end:" + name + ":" + output + ":" + success);
      }

      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
              @NonNull Throwable cause) {
      }
    };
    var process = Dsl.process("Lifecycle")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var ctx = contextFactory.of("input-body", ExecutionMode.RUN, "run-lifecycle")
            .withExecutionListener(listener);

    runner.run(process, ctx);

    assertThat(events).containsExactly(
            "start:Lifecycle:input-body",
            "end:Lifecycle:ok:true");
  }

  @Test
  void runModeEmitsProcessEndWithSuccessFalseWhenExecuteFails() {
    var events = new ArrayList<String>();
    var listener = new ExecutionListener() {
      @Override
      public void onProcessStart(@NonNull String runId, @NonNull String name,
              @Nullable Object input) {
        events.add("start:" + name);
      }

      @Override
      public void onProcessEnd(@NonNull String runId, @NonNull String name,
              @Nullable Object output, boolean success) {
        events.add("end:" + name + ":" + output + ":" + success);
      }

      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
              @NonNull Throwable cause) {
      }
    };
    var process = Dsl.process("Fail")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("nope")))
            // No compensation — ensure lifecycle events still fire even though compensation is
            // skipped
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-fail-no-comp")
            .withExecutionListener(listener);

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(events).containsExactly(
            "start:Fail",
            "end:Fail:null:false");
  }

  @Test
  void runModePropagatesExecuteExceptionAsFailureResultWithDslExecutionExceptionCause() {
    var process = Dsl.process("Throwing")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              throw new IllegalArgumentException("boom-arg");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-throw");

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(DslExecutionException.class);
    assertThat(result.cause().getMessage()).contains("boom-arg");
    assertThat(result.cause().getCause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runModeWithoutListenerStillReturnsResult() {
    var process = Dsl.process("NoListener")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("silent"))
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-no-listener");

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("silent");
  }

  @Test
  void runModeResultValueEqualsExecuteLogicReturnValue() {
    var capturedOutputs = new ArrayList<Object>();
    var process = Dsl.process("ValueProp")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              capturedOutputs.add("exec-ran");
              return Result.success("value-from-execute");
            })
            .build();
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-prop");

    var result = runner.run(process, ctx);

    assertThat(capturedOutputs).containsExactly("exec-ran");
    assertThat(result.value()).isEqualTo("value-from-execute");
    assertThat(result.cause()).isNull();
  }

  @Test
  void runModeEmitsProcessStartBeforeExecuteAndProcessEndAfter() {
    var events = new ArrayList<String>();
    var listener = new ExecutionListener() {
      @Override
      public void onProcessStart(@NonNull String runId, @NonNull String name,
              @Nullable Object input) {
        events.add("start");
      }

      @Override
      public void onProcessEnd(@NonNull String runId, @NonNull String name,
              @Nullable Object output, boolean success) {
        events.add("end");
      }

      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
              @NonNull Throwable cause) {
      }
    };
    var process = Dsl.process("Ordering")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              events.add("execute");
              return Result.success(null);
            })
            .build();
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-order")
            .withExecutionListener(listener);

    runner.run(process, ctx);

    assertThat(events).containsExactly("start", "execute", "end");
  }

  @Test
  void runModeForwardsFailureCauseMessageIntoDslExecutionException() {
    var process = Dsl.process("WithCause")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              throw new RuntimeException("cause-message");
            })
            .build();
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-cause");

    var result = runner.run(process, ctx);

    var cause = result.cause();
    assertThat(cause).isNotNull();
    assertThat(cause.getMessage()).isEqualTo("cause-message");
  }
}
