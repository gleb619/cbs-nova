package cbs.dsl.api;

/**
 * Writable registry interface for registering DSL definitions.
 *
 * This interface is extracted to {@code dsl-api} to break the circular dependency between
 * {@code dsl-codegen} and {@code dsl}. Annotation processors in {@code dsl-codegen} generate
 * code against this interface, while the runtime implementation {@link cbs.dsl.impl.ImplRegistry}
 * implements it.
 */
public interface WritableRegistry {
  /** Register a transaction definition */
  void register(TransactionDefinition t);

  /** Register a helper definition */
  void register(HelperDefinition h);

  /** Register a condition definition */
  void register(ConditionDefinition c);

  /** Register a workflow definition */
  void register(WorkflowDefinition w);

  /** Register a mass operation definition */
  void register(MassOperationDefinition m);
}
