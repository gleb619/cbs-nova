package cbs.nova.dsl.repository;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryTransactionExecutionRepository
        implements
          TransactionExecutionRepository {

  //TODO: redo to a Caffeine with some properties config for ttl
  private final Map<String, List<TransactionExecution>> executions = new ConcurrentHashMap<>();

  @Override
  public @NonNull TransactionExecution save(@NonNull TransactionExecution execution) {
    executions.computeIfAbsent(execution.runId(), _ -> new CopyOnWriteArrayList<>()).add(execution);
    return execution;
  }

  @Override
  public @NonNull List<TransactionExecution> findByRunId(@NonNull String runId) {
    List<TransactionExecution> list = executions.get(runId);
    if (list == null) {
      return List.of();
    }
    List<TransactionExecution> copy = new ArrayList<>(list);
    Collections.reverse(copy);
    return copy;
  }

  @Override
  public void deleteByRunId(@NonNull String runId) {
    executions.remove(runId);
  }
}
