package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public final class DryRunLogBufferRegistry {

  // TODO: redo to a Caffeine with some properties config for ttl
  private final ConcurrentHashMap<String, DryRunLogBuffer> buffers = new ConcurrentHashMap<>();

  public void register(@NonNull String runId, @NonNull DryRunLogBuffer buffer) {
    buffers.put(runId, buffer);
  }

  public @Nullable DryRunLogBuffer get(@NonNull String runId) {
    return buffers.get(runId);
  }

  public @Nullable DryRunLogBuffer remove(@NonNull String runId) {
    return buffers.remove(runId);
  }
}
