package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.fake.FakeConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class RunScopedFakeConfig {

  private static final Duration DEFAULT_TTL = Duration.ofHours(1);
  private static final long DEFAULT_MAX_SIZE = 1_024L;

  private final Cache<String, FakeConfig> configs;

  public RunScopedFakeConfig() {
    this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
  }

  RunScopedFakeConfig(Duration ttl, long maxSize) {
    this.configs = Caffeine.newBuilder()
            .expireAfterAccess(ttl)
            .maximumSize(maxSize)
            .build();
  }

  public void register(@NonNull String runId, @NonNull FakeConfig config) {
    configs.put(runId, config);
  }

  public @Nullable FakeConfig find(@NonNull String runId) {
    return configs.getIfPresent(runId);
  }

  public void remove(@NonNull String runId) {
    configs.invalidate(runId);
  }
}
