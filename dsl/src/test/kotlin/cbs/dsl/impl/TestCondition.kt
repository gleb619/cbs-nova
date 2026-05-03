package cbs.dsl.impl

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.TransactionContext

/**
 * Test implementation of [ConditionDefinition] for use in DSL integration tests and sample `.kts`
 * files.
 *
 * This class provides a simple, configurable condition that can be used to test DSL execution
 * without requiring Spring beans or external dependencies.
 *
 * Example usage in `.kts`:
 * ```kotlin
 * condition("BORROWER_ACCOUNT_READY") {
 *     predicate { ctx -> ctx["accountCode"] != null }
 * }
 * ```
 */
class TestCondition(
    override val code: String,
    override val parameters: List<ParameterDefinition> = emptyList(),
    override val contextBlock: (TransactionContext) -> Unit = {},
    private val _predicate: (TransactionContext) -> Boolean,
) : ConditionDefinition {
  override val predicate: (TransactionContext) -> Boolean
    get() = { ctx ->
      contextBlock(ctx)
      _predicate(ctx)
    }
}
