package cbs.nova.registry;

import cbs.dsl.api.EventOperation;
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

  private final Map<String, TemporalWorkflowOrActivity> activities = new HashMap<>();
  private final Map<String, TemporalWorkflowOrActivity> workflows = new HashMap<>();

  private record TemporalWorkflowOrActivity(Class<?> interfaceClass, Object implementation) {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<EventOperation> asWorkflow() {
      return (Class) interfaceClass();
    }
  }

  @Override
  public void registerActivity(String code, Class<?> activityInterface, Object implementation) {
    activities.put(code, new TemporalWorkflowOrActivity(activityInterface, implementation));
  }

  @Override
  public void registerWorkflow(String code, Class<?> workflowInterface, Object implementation) {
    workflows.put(code, new TemporalWorkflowOrActivity(workflowInterface, implementation));
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
    TemporalWorkflowOrActivity entry = activities.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Activity '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    return entry.interfaceClass();
  }

  @Override
  public <T extends EventOperation> Class<T> getWorkflowInterface(String code) {
    TemporalWorkflowOrActivity entry = workflows.get(code);
    if (entry == null) {
      throw new IllegalArgumentException(
          "Workflow '%s' not found in SpecDefinitionRegistry".formatted(code));
    }
    @SuppressWarnings("unchecked")
    Class<T> result = (Class<T>) entry.interfaceClass();
    return result;
  }

  @Override
  // TODO: add `T extends TransactionOperation` like in `getWorkflow`
  public <T> T getActivity(String code, Class<T> activityInterface) {
    TemporalWorkflowOrActivity entry = activities.get(code);
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
  public <T extends EventOperation> T getWorkflow(String code, Class<T> workflowInterface) {
    TemporalWorkflowOrActivity entry = workflows.get(code);
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
