package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.fake.FakeConfig;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RunScopedFakeConfig {

  // TODO: redo to a Caffeine with some properties config for ttl
  private final Map<String, FakeConfig> configs = new ConcurrentHashMap<>();

  public void register(@NonNull String runId, @NonNull FakeConfig config) {
    configs.put(runId, config);
  }

  public @Nullable FakeConfig find(@NonNull String runId) {
    return configs.get(runId);
  }

  public void remove(@NonNull String runId) {
    configs.remove(runId);
  }
}
