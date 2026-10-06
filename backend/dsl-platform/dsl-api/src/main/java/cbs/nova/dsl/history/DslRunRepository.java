package cbs.nova.dsl.history;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public interface DslRunRepository {

  @NonNull
  DslRun save(@NonNull DslRun run);

  @NonNull
  Optional<DslRun> findByRunId(@NonNull String runId);

  @NonNull
  List<DslRun> findByProcessName(@NonNull String processName);

  @NonNull
  DslRunSearchResult search(
          @Nullable String processName,
          @Nullable String status,
          @Nullable String mode,
          @Nullable String correlationId,
          int offset,
          int limit);

  default @NonNull Set<String> knownProcessNames() {
    return Set.of();
  }

  /**
   * Unconditionally overwrites the terminal fields of the run identified by {@code runId}.
   *
   * <p>
   * This is a raw update: it will overwrite any terminal status, including {@code CANCELLED} or
   * {@code STALE}. Callers that need ordering guarantees should use the guarded variants
   * {@link #updateFinishedIfRunning} (transition only from {@code RUNNING}) or
   * {@link #updateFinishedIfStatusIn} (transition only from an explicit set of statuses).
   */
  @NonNull
  DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);

  int updateFinishedIfRunning(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);

  /**
   * Compare-and-set terminal transition that succeeds only when the run's current status is
   * contained in {@code fromStatuses}. Returns the number of affected rows (0 or 1).
   *
   * <p>
   * The default implementation is a best-effort fallback for repositories that do not implement
   * store-side CAS; it only safely delegates to {@link #updateFinishedIfRunning} when the allowed
   * set is exactly {@code RUNNING}. Real implementations should enforce the status filter
   * atomically in the store.
   */
  default int updateFinishedIfStatusIn(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson,
          @NonNull Set<String> fromStatuses) {
    DslRun run = findByRunId(runId).orElse(null);
    if (run == null || !fromStatuses.contains(run.status())) {
      return 0;
    }
    if (fromStatuses.size() == 1 && fromStatuses.contains(DslRunStatus.RUNNING.name())) {
      return updateFinishedIfRunning(runId, status, output, error, finishedAt, contextJson);
    }
    return 0;
  }

  default int purgeFinishedBefore(@NonNull Instant cutoff, int batchSize) {
    return purgeFinishedBefore(cutoff, batchSize, ids -> {
    });
  }

  default int purgeFinishedBefore(
          @NonNull Instant cutoff,
          int batchSize,
          @NonNull Consumer<List<String>> onBatchBeforeParentDelete) {
    return 0;
  }
}
