package cbs.nova.dsl.repository;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
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

  /**
   * Maximum number of distinct runs retained. Mirrors the sibling
   * {@link InMemoryDslRunRepository} insertion-order eviction pattern so the in-memory history
   * cannot grow without bound.
   */
  private static final int CAPACITY = 100;

  /**
   * Maximum number of executions retained per run; when exceeded the oldest entries are dropped
   * so a single run cannot grow its history without bound either.
   */
  private static final int MAX_EXECUTIONS_PER_RUN = 100;

  // No external cache (e.g. Caffeine): a manual capacity bound is sufficient at this scale while
  // avoiding an extra dependency. Values are immutable snapshots produced under compute(), so
  // concurrent readers are safe without further coordination.
  private final Map<String, List<TransactionExecution>> executions = new ConcurrentHashMap<>();
  private final Deque<String> runOrder = new ConcurrentLinkedDeque<>();
  // Tracks executions.size() explicitly: ConcurrentLinkedDeque#size is an O(n) best-effort scan
  // that may undercount under concurrent writes, which would silently skip evictions.
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
        // First execution for this run — track it for insertion-order eviction.
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

  private void evictOverflowingRuns() {
    while (trackedRuns.get() > CAPACITY) {
      String oldest = runOrder.pollFirst();
      if (oldest == null) {
        return;
      }
      // Removing the run entry also discards its whole transaction list. A null result means a
      // concurrent deleteByRunId already dropped it (and already decremented the counter), so we
      // keep draining stale ids instead of double-counting.
      if (executions.remove(oldest) != null) {
        trackedRuns.decrementAndGet();
      }
    }
  }
}
