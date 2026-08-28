package cbs.nova.dsl.history;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DslRunRepository {

  @NonNull
  DslRun save(@NonNull DslRun run);

  @NonNull
  Optional<DslRun> findByRunId(@NonNull String runId);

  @NonNull
  List<DslRun> findByProcessName(@NonNull String processName);

  default @NonNull Set<String> knownProcessNames() {
    return Set.of();
  }

  @NonNull
  DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);

  /**
   * Compute-and-set finish update that only applies while the run is still {@code RUNNING}.
   *
   * <p>Used by the healthcheck staleness sweep so a stale-marking write cannot overwrite a
   * concurrent terminal transition (COMPLETED/FAILED) performed by the completion path. Returns the
   * number of affected rows: {@code 1} when the run was still RUNNING and was updated, {@code 0}
   * when the run was missing or had already left the RUNNING state (a benign race, not an error).
   */
  int updateFinishedIfRunning(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);
}
