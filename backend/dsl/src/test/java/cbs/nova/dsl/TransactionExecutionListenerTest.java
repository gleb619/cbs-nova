package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class TransactionExecutionListenerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final TransactionRunner runner = new DefaultTransactionRunner(contextFactory,
          new DefaultCompensationRegistry());

  @Test
  void successfulTransactionNotifiesListener() {
    var recorded = new ArrayList<TransactionExecution>();
    var tx = Dsl.transaction("T")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var listener = new ExecutionListener() {
      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
        recorded.add(execution);
      }

      @Override
      public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
              @NonNull Throwable cause) {
      }
    };
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-listen")
            .withExecutionListener(listener);

    runner.run(tx, ctx);

    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).transactionName()).isEqualTo("T");
  }
}
