package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface TransactionDefinition {
  val code: String

  /**
   * Optional display name for this transaction. Used to distinguish DSL overrides from the
   * underlying implementation class/bean identified by [code]. When set, the DSL block is treated
   * as a named override of the bean registered under [code].
   *
   * Example:
   * ```kotlin
   * transaction("KYC_CHECK") {
   *     name("TestKycCheck")
   *     execute { ctx -> ctx["kycVerified"] = true }
   * }
   * ```
   */
  val name: String?
    get() = null

  /**
   * List of parameter definitions declared in the `parameters { }` block. Used for validation and
   * documentation purposes.
   */
  val parameters: List<ParameterDefinition>
    get() = emptyList()

  /**
   * Optional context enrichment block that runs before each phase block (preview, execute,
   * rollback). Allows transactions to enrich the context with additional data before execution.
   *
   * Example:
   * ```kotlin
   * transaction("KYC_CHECK") {
   *     context { ctx ->
   *         ctx["kycProvider"] = "internal"
   *     }
   *     execute { ctx -> ctx["kycVerified"] = true }
   * }
   * ```
   */
  val contextBlock: (TransactionContext) -> Unit
    get() = {}

  fun preview(input: TransactionInput): TransactionOutput

  fun execute(input: TransactionInput): TransactionOutput

  fun rollback(input: TransactionInput): TransactionOutput
}
