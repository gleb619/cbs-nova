package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface TransactionDefinition {
  val code: String

  /**
   * Optional display name for this transaction. Used to distinguish DSL overrides from the
   * underlying implementation class/bean identified by [code]. When set, the DSL block is
   * treated as a named override of the bean registered under [code].
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

  fun preview(ctx: TransactionContext)

  fun execute(ctx: TransactionContext)

  fun rollback(ctx: TransactionContext)
}
