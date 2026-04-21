package cbs.dsl.impl

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.DisplayScope
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.TransactionsScope

/**
 * Test implementation of [EventDefinition] for use in DSL integration tests.
 *
 * This class provides a simple, configurable event that can be used to test DSL execution without
 * requiring Spring beans or external dependencies. It wraps the standard [EventBuilder] pattern
 * but can be constructed programmatically for testing.
 *
 * Example usage:
 * ```kotlin
 * val event = TestEvent(
 *     code = "TEST_EVENT",
 *     parameters = listOf(ParameterDefinition("customerId", true)),
 *     contextBlock = { ctx -> ctx["customer"] = "test" },
 *     transactionsBlock = { step(testTransaction) },
 *     finishBlock = { ctx, ex -> ctx["done"] = true }
 * )
 * ```
 */
class TestEvent(
    override val code: String,
    override val parameters: List<ParameterDefinition> = emptyList(),
    override val contextBlock: (EnrichmentContext) -> Unit = {},
    override val displayBlock: (DisplayScope) -> Unit = {},
    override val transactionsBlock: (suspend TransactionsScope.() -> Unit)? = null,
    override val finishBlock: (FinishContext, Throwable?) -> Unit = { _, _ -> },
) : EventDefinition
