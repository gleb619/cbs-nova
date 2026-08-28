package cbs.nova.starter.logging;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class DryRunLogBufferRegistry {

  private static final Duration DEFAULT_TTL = Duration.ofHours(1);
  private static final long DEFAULT_MAX_SIZE = 1_024L;

  private final Cache<String, DryRunLogBuffer> buffers;

  public DryRunLogBufferRegistry() {
    this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
  }

  public DryRunLogBufferRegistry(Duration ttl, long maxSize) {
    this.buffers = Caffeine.newBuilder()
            .expireAfterAccess(ttl)
            .maximumSize(maxSize)
            .build();
  }

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
