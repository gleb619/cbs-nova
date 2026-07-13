package cbs.nova.dsl.repository;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
}
