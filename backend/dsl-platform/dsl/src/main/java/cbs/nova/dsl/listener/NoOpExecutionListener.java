package cbs.nova.dsl.listener;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class NoOpExecutionListener implements ExecutionListener {

  public static final NoOpExecutionListener INSTANCE = new NoOpExecutionListener();

  private NoOpExecutionListener() {
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
  }

  @Override
  public void onProcessStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  @Override
  public void onProcessEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  @Override
  public void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  @Override
  public void onHelperStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }

  @Override
  public void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
  }
}
