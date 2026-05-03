package cbs.dsl.impl

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.TransactionContext
import java.util.function.Consumer
import java.util.function.Predicate

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
    override val contextBlock: Consumer<TransactionContext> = Consumer { },
    private val _predicate: Predicate<TransactionContext>,
) : ConditionDefinition {
  override val predicate: Predicate<TransactionContext>
    get() = Predicate { ctx ->
      contextBlock.accept(ctx)
      _predicate.test(ctx)
    }
}
