package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface ConditionDefinition {
  val code: String

  /**
   * List of parameter definitions declared in the `parameters { }` block.
   * Used for validation and documentation purposes.
   */
  val parameters: List<ParameterDefinition>
    get() = emptyList()

  /**
   * Optional context enrichment block that runs before the predicate is evaluated.
   * Allows conditions to enrich the context with additional data before evaluation.
   *
   * Example:
   * ```kotlin
   * condition("BORROWER_READY") {
   *     context { ctx ->
   *         ctx["enriched"] = true
   *     }
   *     predicate { ctx -> ctx["enriched"] == true }
   * }
   * ```
   */
  val contextBlock: (TransactionContext) -> Unit
    get() = {}

  val predicate: (TransactionContext) -> Boolean
}
