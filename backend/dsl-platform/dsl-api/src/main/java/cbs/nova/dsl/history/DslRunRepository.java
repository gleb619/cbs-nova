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
   * <p>
   * Used by the healthcheck staleness sweep so a stale-marking write cannot overwrite a concurrent
   * terminal transition (COMPLETED/FAILED) performed by the completion path. Returns the number of
   * affected rows: {@code 1} when the run was still RUNNING and was updated, {@code 0} when the run
   * was missing or had already left the RUNNING state (a benign race, not an error).
   */
  int updateFinishedIfRunning(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);

  /**
   * Deletes finished runs whose {@code finished_at} is strictly before {@code cutoff}.
   *
   * <p>
   * Rows still in the {@code RUNNING} state never match this predicate — a run is only eligible
   * once it has reached a terminal status (COMPLETED/FAILED/STALE/CANCELLED) with a set
   * {@code finished_at}, which makes the delete naturally safe against a row mid-transition out of
   * {@code RUNNING}. Deletion is executed in bounded batches of {@code batchSize} so a first purge
   * of a huge table does not hold row locks for a long stretch; this call loops until a single pass
   * deletes fewer than {@code batchSize} rows.
   *
   * <p>
   * The default implementation is a no-op so in-memory/alternative stores are not forced to
   * implement retention; store-backed repositories override it.
   *
   * @param cutoff
   *          eligibility threshold; only rows with {@code finished_at < cutoff} are purged
   * @param batchSize
   *          max rows removed per batch pass (must be positive)
   * @return the total number of rows deleted
   */
  default int purgeFinishedBefore(@NonNull Instant cutoff, int batchSize) {
    return 0;
  }
}
