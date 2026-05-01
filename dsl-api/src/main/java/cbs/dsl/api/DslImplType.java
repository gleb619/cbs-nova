package cbs.dsl.api;

/**
 * Defines the type of DSL implementation being registered.
 *
 * <p>This tells the annotation processor (or Spring scanner) which registry map to populate in the
 * {@code ImplRegistry}.
 */
public enum DslImplType {
  /**
   * A transaction implementation that will be registered in the transaction registry. Used for
   * classes implementing {@link TransactionFunction}.
   */
  TRANSACTION,

  /**
   * A helper implementation that will be registered in the helper registry. Used for classes
   * implementing {@link HelperFunction}.
   */
  HELPER,

  /**
   * A condition implementation that will be registered in the condition registry. Used for classes
   * implementing {@link ConditionFunction}.
   */
  CONDITION,

  /**
   * An event implementation that will be registered in the event registry. Used for classes
   * implementing {@link EventFunction}.
   */
  EVENT,

  /**
   * A workflow implementation that will be registered in the workflow registry. Used for classes
   * implementing {@link WorkflowFunction}.
   */
  WORKFLOW,

  /**
   * A mass operation implementation that will be registered in the mass operation registry. Used
   * for classes implementing {@link MassOperationFunction}.
   */
  MASS_OPERATION,
}
