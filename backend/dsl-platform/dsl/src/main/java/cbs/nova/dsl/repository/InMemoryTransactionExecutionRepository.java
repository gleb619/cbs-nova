package cbs.nova.dsl.repository;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryTransactionExecutionRepository
        implements
          TransactionExecutionRepository {

  private static final int CAPACITY = 100;

  private static final int MAX_EXECUTIONS_PER_RUN = 100;

  private final Map<String, List<TransactionExecution>> executions = new ConcurrentHashMap<>();
  private final Deque<String> runOrder = new ConcurrentLinkedDeque<>();
  private final AtomicInteger trackedRuns = new AtomicInteger();

  @Override
  public @NonNull TransactionExecution save(@NonNull TransactionExecution execution) {
    String runId = execution.runId();
    AtomicBoolean firstForRun = new AtomicBoolean();
    executions.compute(runId, (id, current) -> {
      List<TransactionExecution> updated = new ArrayList<>(
              current == null ? 1 : current.size() + 1);
      if (current != null) {
        updated.addAll(current);
      } else {
        runOrder.addLast(runId);
        trackedRuns.incrementAndGet();
        firstForRun.set(true);
      }
      updated.add(execution);
      if (updated.size() > MAX_EXECUTIONS_PER_RUN) {
        updated.remove(0);
      }
      return updated;
    });
    if (firstForRun.get()) {
      evictOverflowingRuns();
    }
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
    if (executions.remove(runId) != null) {
      runOrder.remove(runId);
      trackedRuns.decrementAndGet();
    }
  }

  @Override
  public int deleteByRunIds(@NonNull Collection<String> runIds) {
    int deleted = 0;
    for (String runId : runIds) {
      List<TransactionExecution> removed = executions.remove(runId);
      if (removed != null) {
        runOrder.remove(runId);
        trackedRuns.decrementAndGet();
        deleted += removed.size();
      }
    }
    return deleted;
  }

  private void evictOverflowingRuns() {
    while (trackedRuns.get() > CAPACITY) {
      String oldest = runOrder.pollFirst();
      if (oldest == null) {
        return;
      }
      if (executions.remove(oldest) != null) {
        trackedRuns.decrementAndGet();
      }
    }
  }
}
