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
}
