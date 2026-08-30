package cbs.nova.dsl.history;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

public interface TransactionExecutionRepository {

  @NonNull
  TransactionExecution save(@NonNull TransactionExecution execution);

  @NonNull
  List<TransactionExecution> findByRunId(@NonNull String runId);

  void deleteByRunId(@NonNull String runId);

  default int deleteByRunIds(@NonNull Collection<String> runIds) {
    runIds.forEach(this::deleteByRunId);
    return runIds.size();
  }
}
