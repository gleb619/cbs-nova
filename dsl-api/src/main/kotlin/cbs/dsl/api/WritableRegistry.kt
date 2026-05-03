package cbs.dsl.api

/**
 * Writable registry interface for registering DSL definitions.
 *
 * This interface is extracted to [dsl-api] to break the circular dependency between
 * [dsl-codegen] and [dsl]. Annotation processors in [dsl-codegen] generate code against
 * this interface, while the runtime implementation [cbs.dsl.impl.ImplRegistry] implements it.
 */
interface WritableRegistry {
  /** Register a transaction definition */
  fun register(t: TransactionDefinition)

  /** Register a helper definition */
  fun register(h: HelperDefinition)

  /** Register a condition definition */
  fun register(c: ConditionDefinition)

  /** Register a workflow definition */
  fun register(w: WorkflowDefinition)

  /** Register a mass operation definition */
  fun register(m: MassOperationDefinition)
}
