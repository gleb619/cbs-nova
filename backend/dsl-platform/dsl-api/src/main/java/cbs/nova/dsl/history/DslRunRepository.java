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
