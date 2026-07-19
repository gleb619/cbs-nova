package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Per-thread context that marks the current execution as a DSL dry-run (preview/explain) and holds
 * the runId used to correlate captured log events.
 */
public final class DryRunLoggingContext {

  private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

  private DryRunLoggingContext() {
  }

  /** Activate dry-run logging for the current thread. */
  public static void enterDryRun(@NonNull String runId) {
    RUN_ID.set(runId);
  }

  /** Deactivate dry-run logging for the current thread. */
  public static void leaveDryRun() {
    RUN_ID.remove();
  }

  /** Return the active dry-run runId, or {@code null} if none. */
  public static @Nullable String currentRunId() {
    return RUN_ID.get();
  }
}
