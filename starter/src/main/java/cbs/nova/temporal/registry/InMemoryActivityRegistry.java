package cbs.nova.temporal.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory {@link ActivityRegistry} backed by an immutable map.
 *
 * <p>Intended for demonstration and testing. Production systems can replace this with an
 * implementation that loads definitions from the {@code DslRegistry} or from external
 * configuration.
 *
 * <p>Because the map is copied and wrapped unmodifiable at construction time, this registry is
 * deterministic and safe for concurrent access.
 */
// TODO: remove file
@Deprecated(forRemoval = true)
public class InMemoryActivityRegistry implements ActivityRegistry {

  private final Map<String, ActivityConfig> configs;

  public InMemoryActivityRegistry(Map<String, ActivityConfig> configs) {
    this.configs = Collections.unmodifiableMap(new HashMap<>(configs));
  }

  @Override
  public ActivityConfig getActivityConfig(String logicalName) {
    ActivityConfig config = configs.get(logicalName);
    if (config == null) {
      throw new IllegalArgumentException(
          "Activity '%s' not found in InMemoryActivityRegistry".formatted(logicalName));
    }
    return config;
  }
}
