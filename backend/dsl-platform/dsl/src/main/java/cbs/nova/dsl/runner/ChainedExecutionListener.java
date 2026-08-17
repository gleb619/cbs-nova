package cbs.nova.dsl.runner;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class ChainedExecutionListener implements ExecutionListener {

  private final ExecutionListener first;
  private final ExecutionListener second;

  ChainedExecutionListener(@NonNull ExecutionListener first, @NonNull ExecutionListener second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public void onProcessStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    first.onProcessStart(runId, name, input);
    second.onProcessStart(runId, name, input);
  }

  @Override
  public void onProcessEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    first.onProcessEnd(runId, name, output, success);
    second.onProcessEnd(runId, name, output, success);
  }

  @Override
  public void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    first.onTransactionStart(runId, name, input);
    second.onTransactionStart(runId, name, input);
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    first.onTransactionEnd(runId, name, output, success);
    second.onTransactionEnd(runId, name, output, success);
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    first.onTransactionSuccess(execution);
    second.onTransactionSuccess(execution);
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
    first.onTransactionFailure(runId, transactionName, cause);
    second.onTransactionFailure(runId, transactionName, cause);
  }

  @Override
  public void onHelperStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    first.onHelperStart(runId, name, input);
    second.onHelperStart(runId, name, input);
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    first.onHelperEnd(runId, name, output, success);
    second.onHelperEnd(runId, name, output, success);
  }

  @Override
  public void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    first.onFunctionStart(runId, name, input);
    second.onFunctionStart(runId, name, input);
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    first.onFunctionEnd(runId, name, output, success);
    second.onFunctionEnd(runId, name, output, success);
  }
}
