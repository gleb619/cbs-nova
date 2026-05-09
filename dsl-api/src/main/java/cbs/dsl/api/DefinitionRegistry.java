package cbs.dsl.api;

/**
 * Writable registry interface for registering DSL definitions.
 *
 * <p>This interface is extracted to {@code dsl-api} to break the circular dependency between
 * {@code dsl-codegen} and {@code dsl}. Annotation processors in {@code dsl-codegen} generate code
 * against this interface, while the runtime implementation {@link cbs.nova.registry.DslRegistry}
 * implements it.
 */
public interface DefinitionRegistry {
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

  /**
   * Registers a runtime {@link DslObject} by adapting it to the appropriate {@link DslDefinition}
   * type. Used only in REFLECTED dev mode.
   *
   * @param obj the DSL object to register
   */
  default void register(DslObject obj) {
    Class<?> enclosing = obj.getClass().getEnclosingClass();
    String typeName = enclosing != null ? enclosing.getSimpleName() : "";
    String simpleName = obj.getClass().getSimpleName();
    String resolvedType = typeName.isEmpty() ? simpleName : typeName;

    switch (resolvedType) {
      case "EventBuilder", "EventDslObject" -> {
        if (obj instanceof EventDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, EventDefinition.class));
      }
      case "TransactionBuilder", "TransactionDslObject" -> {
        if (obj instanceof TransactionDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, TransactionDefinition.class));
      }
      case "WorkflowBuilder", "WorkflowDslObject" -> {
        if (obj instanceof WorkflowDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, WorkflowDefinition.class));
      }
      case "HelperBuilder", "HelperDslObject" -> {
        if (obj instanceof HelperDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, HelperDefinition.class));
      }
      case "ConditionBuilder", "ConditionDslObject" -> {
        if (obj instanceof ConditionDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, ConditionDefinition.class));
      }
      case "MassOperationBuilder", "MassOperationDslObject" -> {
        if (obj instanceof MassOperationDefinition def) register(def);
        else register(DslObjectAdapter.adapt(obj, MassOperationDefinition.class));
      }
      default -> throw new IllegalArgumentException("Unsupported builder type: " + resolvedType);
    }
  }

  /**
   * Returns the component resolver associated with this registry, or {@code null} if none.
   *
   * <p>Generated {@code GeneratedImplRegistrations} can call this method to obtain a resolver and
   * pass it to generated wrapper constructors.
   *
   * @return the resolver, or {@code null}
   */
  default DslComponentResolver getComponentResolver() {
    return null;
  }

  /**
   * Resolves a workflow definition by code.
   *
   * @param code the workflow code
   * @return the workflow definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default WorkflowDefinition resolveWorkflow(String code) {
    throw new UnsupportedOperationException("Workflow resolution not supported by this registry");
  }

  /**
   * Resolves an event definition by code.
   *
   * @param code the event code
   * @return the event definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default EventDefinition resolveEvent(String code) {
    throw new UnsupportedOperationException("Event resolution not supported by this registry");
  }

  /**
   * Resolves a transaction definition by code.
   *
   * @param code the transaction code
   * @return the transaction definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default TransactionDefinition resolveTransaction(String code) {
    throw new UnsupportedOperationException("Transaction resolution not supported by this registry");
  }

  /**
   * Resolves a mass operation definition by code.
   *
   * @param code the mass operation code
   * @return the mass operation definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default MassOperationDefinition resolveMassOperation(String code) {
    throw new UnsupportedOperationException("MassOperation resolution not supported by this registry");
  }

  /**
   * Resolves a helper definition by code.
   *
   * @param code the helper code
   * @return the helper definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default HelperDefinition resolveHelper(String code) {
    throw new UnsupportedOperationException("Helper resolution not supported by this registry");
  }

  /**
   * Resolves a condition definition by code.
   *
   * @param code the condition code
   * @return the condition definition
   * @throws UnsupportedOperationException if this registry does not support resolution
   */
  default ConditionDefinition resolveCondition(String code) {
    throw new UnsupportedOperationException("Condition resolution not supported by this registry");
  }
}
