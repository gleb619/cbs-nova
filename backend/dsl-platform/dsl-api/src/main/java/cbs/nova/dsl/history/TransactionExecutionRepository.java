package cbs.nova.dsl.history;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface TransactionExecutionRepository {

  @NonNull
  TransactionExecution save(@NonNull TransactionExecution execution);

  @NonNull
  List<TransactionExecution> findByRunId(@NonNull String runId);

  void deleteByRunId(@NonNull String runId);
}
