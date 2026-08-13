package cbs.nova.dsl.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * SPI for correlating log events with a dry-run (preview/explain) execution identifier.
 *
 * <p>
 * Implementations store the active {@code runId} in a thread-bound carrier and expose it to callers
 * via {@link #currentRunId()}. The canonical way to activate a dry-run context for a block of work
 * is {@link #runWithRunId(String, Runnable)}; this works for both explicit
 * {@link #setRunId(String)}/{@link #clearRunId()} implementations.
 *
 * <p>
 * The header key below is used when propagating the runId across Temporal workflow/activity nodes.
 */
public interface DryRunLoggingContext {

  /**
   * Temporal (and generic RPC) header key used to carry the dry-run runId across nodes.
   */
  String RUN_ID_HEADER = "x-cbs-nova-dry-run-run-id";

  /**
   * Activates {@code runId} for the duration of {@code action}, then clears it.
   *
   * <p>
   * Implementations must guarantee that the runId is visible to {@link #currentRunId()} while
   * {@code action} runs and is cleared on both normal and exceptional completion.
   */
  default void runWithRunId(@NonNull String runId, @NonNull Runnable action) {
    setRunId(runId);
    try {
      action.run();
    } finally {
      clearRunId();
    }
  }

  /** Activate the given runId in the current execution scope. */
  void setRunId(@Nullable String runId);

  /** Deactivate the current runId. */
  void clearRunId();

  /** Return the active dry-run runId, or {@code null} if none. */
  @Nullable
  String currentRunId();
}
