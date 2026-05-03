package cbs.dsl.api

interface HelperDefinition : HelperFunction<HelperInput, HelperOutput> {
  val code: String

  /**
   * Optional display name for this helper. Used to distinguish DSL overrides from the
   * underlying implementation class/bean identified by [code]. When set, the DSL block is
   * treated as a named override of the bean registered under [code].
   *
   * Example:
   * ```kotlin
   * helper("LOAN_CONDITIONS_BY_ID") {
   *     name("TestLoanConditions")
   *     execute { ctx -> mapOf("loanId" to ctx.params["loanId"], "currency" to "USD") }
   * }
   * ```
   */
  val name: String?
    get() = null

  /**
   * List of parameter definitions declared in the `parameters { }` block.
   * Used for validation and documentation purposes.
   */
  val parameters: List<ParameterDefinition>
    get() = emptyList()

  /**
   * Optional context enrichment block that runs before the execute block.
   * Allows helpers to enrich the context with additional data before execution.
   *
   * Example:
   * ```kotlin
   * helper("LOAN_HELPER") {
   *     context { ctx ->
   *         ctx["defaultCurrency"] = "USD"
   *     }
   *     execute { ctx -> mapOf("currency" to ctx["defaultCurrency"]) }
   * }
   * ```
   */
  val contextBlock: (cbs.dsl.api.context.HelperContext) -> Unit
    get() = {}
}
