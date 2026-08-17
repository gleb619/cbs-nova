package cbs.nova.dsl.runner;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;

@RequiredArgsConstructor
public final class DefaultExecutionListener implements ExecutionListener {

  private final String runId;
  private final TransactionExecutionRepository repository;

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    repository.save(execution);
  }

  @Override
  public void onTransactionFailure(
          @NonNull String transactionRunId,
          @NonNull String transactionName,
          @NonNull Throwable cause) {
  }

  public @NonNull List<TransactionExecution> historyInReverse() {
    return repository.findByRunId(runId);
  }
}
