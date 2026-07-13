package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class TransactionExecutionListenerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final TransactionRunner runner = new DefaultTransactionRunner(
          new ExecutionTraceCollector(), contextFactory);

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
      public void onTransactionSuccess(TransactionExecution execution) {
        recorded.add(execution);
      }

      @Override
      public void onTransactionFailure(String runId, String transactionName, Throwable cause) {
      }
    };
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-1")
            .withExecutionListener(listener);
    runner.run(tx, ctx);
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).transactionName()).isEqualTo("T");
    assertThat(recorded.get(0).runId()).isEqualTo("run-1");
  }
}
