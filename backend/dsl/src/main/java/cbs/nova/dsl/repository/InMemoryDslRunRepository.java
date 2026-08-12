package cbs.nova.dsl.repository;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * In-memory {@link DslRunRepository} implementation retaining the most recent 100 runs.
 *
 * <p>
 * Eviction is insertion-order FIFO: when a new run is saved and the store already holds
 * {@value #CAPACITY} runs, the oldest saved run is removed.
 */
public final class InMemoryDslRunRepository implements DslRunRepository {

  private static final int CAPACITY = 100;

  private final Map<String, DslRun> runs = new LinkedHashMap<>(CAPACITY, 0.75f, false) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, DslRun> eldest) {
      return size() > CAPACITY;
    }
  };

  @Override
  public synchronized @NonNull DslRun save(@NonNull DslRun run) {
    runs.put(run.runId(), run);
    return run;
  }

  @Override
  public synchronized @NonNull Optional<DslRun> findByRunId(@NonNull String runId) {
    return Optional.ofNullable(runs.get(runId));
  }

  @Override
  public synchronized @NonNull List<DslRun> findByProcessName(@NonNull String processName) {
    return runs.values().stream()
            .filter(r -> processName.equals(r.processName()))
            .collect(Collectors.toList());
  }

  @Override
  public synchronized @NonNull Set<String> knownProcessNames() {
    return runs.values().stream()
            .map(DslRun::processName)
            .collect(Collectors.toSet());
  }

  @Override
  public synchronized @NonNull DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson) {
    DslRun existing = runs.get(runId);
    if (existing == null) {
      throw new IllegalStateException("Run not found: " + runId);
    }
    DslRun updated = DslRun.builder()
            .runId(existing.runId())
            .processName(existing.processName())
            .status(status)
            .input(existing.input())
            .output(output)
            .error(error)
            .contextJson(contextJson)
            .startedAt(existing.startedAt())
            .finishedAt(finishedAt)
            .executionMode(existing.executionMode())
            .build();
    runs.put(runId, updated);
    return updated;
  }
}
