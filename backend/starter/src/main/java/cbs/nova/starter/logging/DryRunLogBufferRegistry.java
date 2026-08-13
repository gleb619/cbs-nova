package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lookup table that maps an active dry-run {@code runId} to its per-run {@link DryRunLogBuffer}.
 *
 * <p>
 * The registry intentionally does <em>not</em> own the buffer lifecycle: it is created and removed
 * by the pipe stage. This avoids the leak-prone pattern of a long-lived runId-keyed map that grows
 * forever if a run fails to clean up.
 */
public final class DryRunLogBufferRegistry {

  private final ConcurrentHashMap<String, DryRunLogBuffer> buffers = new ConcurrentHashMap<>();

  /**
   * Register the given buffer under the given runId.
   */
  public void register(@NonNull String runId, @NonNull DryRunLogBuffer buffer) {
    buffers.put(runId, buffer);
  }

  /**
   * Return the buffer currently registered for the given runId, or {@code null} if none.
   */
  public @Nullable DryRunLogBuffer get(@NonNull String runId) {
    return buffers.get(runId);
  }

  /**
   * Remove and return the buffer registered for the given runId, or {@code null} if none.
   */
  public @Nullable DryRunLogBuffer remove(@NonNull String runId) {
    return buffers.remove(runId);
  }
}
