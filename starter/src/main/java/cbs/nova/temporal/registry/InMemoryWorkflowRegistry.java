package cbs.nova.temporal.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory {@link WorkflowRegistry} backed by an immutable map.
 *
 * <p>Intended for demonstration and testing. Production systems can replace this with an
 * implementation that loads definitions from the {@code DslRegistry} or from external
 * configuration.
 *
 * <p>Because the map is copied and wrapped unmodifiable at construction time, this registry is
 * deterministic and safe for concurrent access.
 */
public class InMemoryWorkflowRegistry implements WorkflowRegistry {

  private final Map<String, WorkflowConfig> configs;

  public InMemoryWorkflowRegistry(Map<String, WorkflowConfig> configs) {
    this.configs = Collections.unmodifiableMap(new HashMap<>(configs));
  }

  @Override
  public WorkflowConfig getWorkflowConfig(String logicalName) {
    WorkflowConfig config = configs.get(logicalName);
    if (config == null) {
      throw new IllegalArgumentException(
          "Workflow '" + logicalName + "' not found in InMemoryWorkflowRegistry");
    }
    return config;
  }
}
