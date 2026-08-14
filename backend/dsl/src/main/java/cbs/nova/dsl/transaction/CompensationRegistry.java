package cbs.nova.dsl;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;

/**
 * Per-run registry of transaction compensations.
 *
 * <p>
 * Implementations must be thread-safe. All public methods establish happens-before edges through
 * concurrent collections, so actions in a successfully executed compensation are visible to
 * subsequent callers.
 *
 * <p>
 * Each compensation entry is executed at most once: even if {@link #compensate} and
 * {@link #compensateAll} race for the same entry, only one caller will win and run it; after it has
 * been removed from the registry it will never run again.
 *
 * <p>
 * Compensations are stored per {@code runId}. {@link #clear()} drops every known run.
 */
public interface CompensationRegistry {

  /**
   * Registers the compensation of {@code transaction} for {@code runId}.
   *
   * @return {@code true} if a compensation logic exists and was stored, {@code false} otherwise
   */
  boolean register(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Context<?> baseCtx,
          @NonNull TransactionDslObject transaction);

  /**
   * Finds the most recently registered matching compensation for {@code transactionName} and
   * {@code runId}, removes it, and executes it with the supplied error.
   *
   * <p>
   * If no matching compensation exists, this method does nothing.
   */
  void compensate(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory);

  /**
   * Removes and executes every compensation registered for {@code runId} in reverse registration
   * order (LIFO).
   *
   * <p>
   * Entries that have already been executed by a concurrent {@link #compensate} call are skipped so
   * that each compensation runs at most once.
   */
  void compensateAll(
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory);

  /** Returns {@code true} if there is at least one pending compensation for {@code runId}. */
  boolean hasCompensation(@NonNull String runId);

  /** Clears all pending compensations for all runs. */
  void clear();
}
