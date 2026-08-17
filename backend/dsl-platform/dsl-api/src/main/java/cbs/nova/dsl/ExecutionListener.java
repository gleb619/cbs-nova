package cbs.nova.dsl;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface ExecutionListener {

  void onTransactionSuccess(@NonNull TransactionExecution execution);

  void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause);

  default void onProcessStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  default void onProcessEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  default void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  default void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  default void onHelperStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  default void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  default void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  default void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }
}
