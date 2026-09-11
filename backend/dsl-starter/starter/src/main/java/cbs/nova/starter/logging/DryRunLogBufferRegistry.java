package cbs.nova.starter.logging;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class DryRunLogBufferRegistry {

  private final @NonNull Cache<String, DryRunLogBuffer> buffers;

  public void register(@NonNull String runId, @NonNull DryRunLogBuffer buffer) {
    buffers.put(runId, buffer);
  }

  public @Nullable DryRunLogBuffer get(@NonNull String runId) {
    return buffers.getIfPresent(runId);
  }

  public @Nullable DryRunLogBuffer remove(@NonNull String runId) {
    DryRunLogBuffer previous = buffers.getIfPresent(runId);
    buffers.invalidate(runId);
    return previous;
  }
}
