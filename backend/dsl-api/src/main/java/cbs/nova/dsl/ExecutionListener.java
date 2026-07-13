package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface ExecutionListener {

  void onTransactionSuccess(@NonNull TransactionExecution execution);

  void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause);
}
