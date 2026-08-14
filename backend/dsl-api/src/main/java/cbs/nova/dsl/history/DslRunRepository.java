package cbs.nova.dsl.history;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for persisting DSL process runs.
 */
public interface DslRunRepository {

  @NonNull
  DslRun save(@NonNull DslRun run);

  @NonNull
  Optional<DslRun> findByRunId(@NonNull String runId);

  @NonNull
  List<DslRun> findByProcessName(@NonNull String processName);

  /**
   * Returns the distinct set of process names that currently have at least one persisted run.
   * Backed by the repository's internal bookkeeping; the default returns an empty set for
   * implementations that do not track this (e.g. a thin facade).
   */
  default @NonNull Set<String> knownProcessNames() {
    return Set.of();
  }

  /**
   * Targeted update that mutates only the fields that change when a run finishes.
   *
   * @return the updated run
   * @throws IllegalStateException
   *           if the runId does not exist
   */
  @NonNull
  DslRun updateFinished(
          @NonNull String runId,
          @NonNull String status,
          @Nullable String output,
          @Nullable String error,
          @NonNull Instant finishedAt,
          @Nullable String contextJson);
}
