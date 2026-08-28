package cbs.nova.dsl.repository;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

public final class InMemoryDslRunRepository implements DslRunRepository {

  private static final int CAPACITY = 100;

  // TODO: redo to a Caffeine with some properties config for ttl
  private final Map<String, DslRun> runs = new ConcurrentHashMap<>();
  private final Deque<String> insertionOrder = new ConcurrentLinkedDeque<>();

  @Override
  public @NonNull DslRun save(@NonNull DslRun run) {
    DslRun previous = runs.put(run.runId(), run);
    if (previous == null) {
      insertionOrder.addLast(run.runId());
      if (insertionOrder.size() > CAPACITY) {
        String oldest = insertionOrder.pollFirst();
        if (oldest != null) {
          runs.remove(oldest);
        }
      }
    }
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
    return runs.values().stream()
            .map(DslRun::processName)
            .collect(Collectors.toSet());
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
