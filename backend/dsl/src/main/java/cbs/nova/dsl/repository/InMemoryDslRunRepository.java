package cbs.nova.dsl.repository;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link DslRunRepository} implementation backed by a concurrent map.
 */
public final class InMemoryDslRunRepository implements DslRunRepository {

  private final Map<String, DslRun> runs = new ConcurrentHashMap<>();

  @Override
  public @NonNull DslRun save(@NonNull DslRun run) {
    runs.put(run.runId(), run);
    return run;
  }

  @Override
  public @NonNull Optional<DslRun> findByRunId(@NonNull String runId) {
    return Optional.ofNullable(runs.get(runId));
  }

  @Override
  public @NonNull List<DslRun> findByProcessName(@NonNull String processName) {
    return runs.values().stream()
            .filter(r -> processName.equals(r.processName()))
            .collect(Collectors.toList());
  }

  @Override
  public @NonNull Set<String> knownProcessNames() {
    Set<String> names = new HashSet<>();
    for (DslRun run : runs.values()) {
      names.add(run.processName());
    }
    return names;
  }

  @Override
  public @NonNull DslRun updateFinished(
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
