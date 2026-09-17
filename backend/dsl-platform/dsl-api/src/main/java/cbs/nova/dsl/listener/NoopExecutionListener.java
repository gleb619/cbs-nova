package cbs.nova.dsl.listener;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

public final class NoopExecutionListener implements ExecutionListener {

  public static final NoopExecutionListener INSTANCE = new NoopExecutionListener();

  private NoopExecutionListener() {
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
  }
}
