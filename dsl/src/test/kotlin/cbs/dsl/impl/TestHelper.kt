package cbs.dsl.impl

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.HelperContext

/**
 * Test implementation of [HelperDefinition] for use in DSL integration tests and sample `.kts`
 * files.
 *
 * This class provides a simple, configurable helper that can be used to test DSL execution without
 * requiring Spring beans or external dependencies.
 *
 * Example usage in `.kts`:
 * ```kotlin
 * helper("FIND_CUSTOMER_CODE_BY_ID") {
 *     name("TestFindCustomerCode")
 *     execute { ctx -> "CUST-${ctx.params["id"]}" }
 * }
 * ```
 *
 * The `name` field distinguishes this test implementation from a production bean with the same
 * `code`.
 */
class TestHelper(
    override val code: String,
    override val name: String? = null,
    override val parameters: List<ParameterDefinition> = emptyList(),
    override val contextBlock: (HelperContext) -> Unit = {},
    private val executeBlock: (Map<String, Any>) -> Any,
) : HelperDefinition {
  override fun execute(input: HelperInput): HelperOutput {
    val ctx =
        HelperContext(
            eventCode = input.eventCode() ?: "",
            workflowExecutionId = input.workflowExecutionId() ?: 0L,
            performedBy = "",
            dslVersion = "",
            params = input.params(),
        )

    contextBlock(ctx)

    val result = executeBlock(input.params())
    return HelperOutput(result)
  }
}
