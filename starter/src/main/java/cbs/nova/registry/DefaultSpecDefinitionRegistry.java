package cbs.nova.registry;

import cbs.dsl.api.SpecDefinitionRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default in-memory implementation of {@link SpecDefinitionRegistry}.
 *
 * <p>Stores activity and workflow definitions by code and supports type-safe lookups. Populated at
 * startup via SPI-loaded {@link cbs.dsl.api.SpecDefinitionRegistryProvider} implementations.
 */
public class DefaultSpecDefinitionRegistry implements SpecDefinitionRegistry {

  private final Map<String, ArtifactEntry> activities = new HashMap<>();
  private final Map<String, ArtifactEntry> workflows = new HashMap<>();

  private record ArtifactEntry(Class<?> interfaceClass, Object implementation) {}

  @Override
  public void registerActivity(String code, Class<?> activityInterface, Object implementation) {
    activities.put(code, new ArtifactEntry(activityInterface, implementation));
  }

  @Override
  public void registerWorkflow(String code, Class<?> workflowInterface, Object implementation) {
    workflows.put(code, new ArtifactEntry(workflowInterface, implementation));
  }

  @Override
  public Set<String> getActivityCodes() {
    return Collections.unmodifiableSet(activities.keySet());
  }

  @Override
  public Set<String> getWorkflowCodes() {
    return Collections.unmodifiableSet(workflows.keySet());
  }

  @Override
  public Class<?> getActivityInterface(String code) {
    ArtifactEntry entry = activities.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Activity '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    return entry.interfaceClass();
  }

  @Override
  public Class<?> getWorkflowInterface(String code) {
    ArtifactEntry entry = workflows.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Workflow '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    return entry.interfaceClass();
  }

  @Override
  public <T> T getActivity(String code, Class<T> activityInterface) {
    ArtifactEntry entry = activities.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Activity '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    if (!entry.interfaceClass().equals(activityInterface)) {
      throw new IllegalArgumentException(
          "Activity '%s' is registered with interface %s, requested %s"
              .formatted(code, entry.interfaceClass().getName(), activityInterface.getName()));
    }
    return activityInterface.cast(entry.implementation());
  }

  @Override
  public <T> T getWorkflow(String code, Class<T> workflowInterface) {
    ArtifactEntry entry = workflows.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Workflow '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    if (!entry.interfaceClass().equals(workflowInterface)) {
      throw new IllegalArgumentException(
          "Workflow '%s' is registered with interface %s, requested %s"
              .formatted(code, entry.interfaceClass().getName(), workflowInterface.getName()));
    }
    return workflowInterface.cast(entry.implementation());
  }
}
