package cbs.nova.dsl.runner;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DefaultExecutionListener implements ExecutionListener {

  private final List<TransactionExecution> successful = Collections
          .synchronizedList(new ArrayList<>());

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    successful.add(execution);
  }

  @Override
  public void onTransactionFailure(
          @NonNull String runId,
          @NonNull String transactionName,
          @NonNull Throwable cause) {
  }

  public @NonNull List<TransactionExecution> historyInReverse() {
    synchronized (successful) {
      List<TransactionExecution> copy = new ArrayList<>(successful);
      Collections.reverse(copy);
      return List.copyOf(copy);
    }
  }
}
