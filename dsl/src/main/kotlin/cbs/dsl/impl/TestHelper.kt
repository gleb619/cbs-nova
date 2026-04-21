package cbs.dsl.impl

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperInput
import cbs.dsl.api.HelperOutput
import cbs.dsl.runtime.AnyHelperOutput
import cbs.dsl.runtime.MapHelperInput

/**
 * Test implementation of [HelperDefinition] for use in DSL integration tests and sample `.kts` files.
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
 * The `name` field distinguishes this test implementation from a production bean with the same `code`.
 */
class TestHelper(
    override val code: String,
    override val name: String? = null,
    private val executeBlock: (Map<String, Any>) -> Any,
) : HelperDefinition {
    override fun execute(input: HelperInput): HelperOutput {
        val mapInput =
            input as? MapHelperInput
                ?: error("TestHelper input must be MapHelperInput")
        val result = executeBlock(mapInput.params)
        return AnyHelperOutput(result)
    }
}
