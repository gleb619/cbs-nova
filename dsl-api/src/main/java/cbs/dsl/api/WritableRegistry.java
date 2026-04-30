package cbs.dsl.api;

/**
 * Writable registry interface for registering DSL definitions.
 *
 * <p>This interface is extracted to {@code dsl-api} to break the circular dependency between
 * {@code dsl-codegen} and {@code dsl}. Annotation processors in {@code dsl-codegen} generate code
 * against this interface, while the runtime implementation {@link cbs.nova.registry.DslRegistry}
 * implements it.
 */
public interface WritableRegistry {
  /**
   * Registers a transaction definition.
   *
   * @param t the transaction definition to register
   */
  void register(TransactionDefinition t);

  /**
   * Registers a helper definition.
   *
   * @param h the helper definition to register
   */
  void register(HelperDefinition h);

  /**
   * Registers a condition definition.
   *
   * @param c the condition definition to register
   */
  void register(ConditionDefinition c);

  /**
   * Registers a workflow definition.
   *
   * @param w the workflow definition to register
   */
  void register(WorkflowDefinition w);

  /**
   * Registers a mass operation definition.
   *
   * @param m the mass operation definition to register
   */
  void register(MassOperationDefinition m);

  /**
   * Registers an event definition.
   *
   * @param e the event definition to register
   */
  void register(EventDefinition e);
}
