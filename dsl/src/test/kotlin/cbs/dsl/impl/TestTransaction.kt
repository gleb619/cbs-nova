package cbs.dsl.impl

import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionContext

/**
 * Test implementation of [TransactionDefinition] for use in DSL integration tests and sample `.kts`
 * files.
 *
 * This class provides a simple, configurable transaction that can be used to test DSL execution
 * without requiring Spring beans or external dependencies.
 *
 * Example usage in `.kts`:
 * ```kotlin
 * transaction("KYC_CHECK") {
 *     name("TestKycCheck")
 *     execute { ctx -> ctx["kycVerified"] = true }
 * }
 * ```
 *
 * The `name` field distinguishes this test implementation from a production bean with the same
 * `code`.
 */
class TestTransaction(
    override val code: String,
    override val name: String? = null,
    override val parameters: List<ParameterDefinition> = emptyList(),
    override val contextBlock: (TransactionContext) -> Unit = {},
    private val previewBlock: ((TransactionContext) -> Unit)? = null,
    private val executeBlock: ((TransactionContext) -> Unit)? = null,
    private val rollbackBlock: ((TransactionContext) -> Unit)? = null,
) : TransactionDefinition {
  override fun preview(ctx: TransactionContext) {
    contextBlock(ctx)
    previewBlock?.invoke(ctx)
  }

  override fun execute(ctx: TransactionContext) {
    contextBlock(ctx)
    executeBlock?.invoke(ctx) ?: error("TestTransaction '$code' has no execute block defined")
  }

  override fun rollback(ctx: TransactionContext) {
    contextBlock(ctx)
    rollbackBlock?.invoke(ctx)
  }
}
