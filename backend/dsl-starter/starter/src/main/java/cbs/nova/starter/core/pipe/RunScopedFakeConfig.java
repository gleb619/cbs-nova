package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.fake.FakeConfig;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
public final class RunScopedFakeConfig {

  private final Cache<String, FakeConfig> configs;

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
