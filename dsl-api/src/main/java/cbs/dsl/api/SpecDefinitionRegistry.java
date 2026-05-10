package cbs.dsl.api;

import java.util.Set;

/**
 * Registry for code-generated Temporal artifacts (activities and workflows).
 *
 * <p>Generated {@link SpecDefinitionRegistryProvider} implementations (e.g.
 * {@code SpecDefinitionRegistryProviderImpl}) are produced at compile time by the annotation
 * processor and contain all known activity and workflow definitions baked in. They are discovered
 * via {@link java.util.ServiceLoader} and register into a runtime registry.
 *
 * <p>This interface is placed in {@code dsl-api} so that both generated code and the
 * {@code starter} managers can depend on it without creating a circular module dependency.
 */
public interface SpecDefinitionRegistry {

  /**
   * Registers a generated activity implementation under its canonical code.
   *
   * @param code the activity code (e.g. {@code "DEBIT_FUNDING_ACCOUNT"})
   * @param activityInterface the Temporal {@code @ActivityInterface} class
   * @param implementation the generated definition that implements the interface
   */
  void registerActivity(String code, Class<?> activityInterface, Object implementation);

  /**
   * Registers a generated workflow implementation under its canonical code.
   *
   * @param code the workflow / event code (e.g. {@code "LOAN_DISBURSEMENT"})
   * @param workflowInterface the Temporal {@code @WorkflowInterface} class
   * @param implementation the generated definition that implements the interface
   */
  void registerWorkflow(String code, Class<?> workflowInterface, Object implementation);

  /**
   * Returns all registered activity codes.
   *
   * @return unmodifiable set of activity codes
   */
  Set<String> getActivityCodes();

  /**
   * Returns all registered workflow codes.
   *
   * @return unmodifiable set of workflow codes
   */
  Set<String> getWorkflowCodes();

  /**
   * Returns the activity interface class registered under the given code.
   *
   * @param code the activity code
   * @return the interface class
   * @throws IllegalArgumentException if no activity is registered with the given code
   */
  Class<?> getActivityInterface(String code);

  /**
   * Returns the workflow interface class registered under the given code.
   *
   * @param code the workflow code
   * @return the interface class
   * @throws IllegalArgumentException if no workflow is registered with the given code
   */
  <T extends EventOperation> Class<T> getWorkflowInterface(String code);

  /**
   * Looks up an activity implementation by code and casts it to the requested interface type.
   *
   * @param code the activity code
   * @param activityInterface the expected interface class
   * @param <T> the activity type
   * @return the implementation cast to the requested type
   * @throws IllegalArgumentException if the code is unknown or the cast fails
   */
  <T> T getActivity(String code, Class<T> activityInterface);

  /**
   * Looks up a workflow implementation by code and casts it to the requested interface type.
   *
   * @param code the workflow code
   * @param workflowInterface the expected interface class
   * @param <T> the workflow type
   * @return the implementation cast to the requested type
   * @throws IllegalArgumentException if the code is unknown or the cast fails
   */
  <T extends EventOperation> T getWorkflow(String code, Class<T> workflowInterface);
}
