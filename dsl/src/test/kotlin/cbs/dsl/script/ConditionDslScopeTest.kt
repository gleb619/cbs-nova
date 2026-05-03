package cbs.dsl.script

import cbs.dsl.api.context.TransactionContext
import cbs.dsl.runtime.ConditionBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConditionDslScopeTest {
    @Test
    @DisplayName("shouldRegisterConditionWhenConditionFunctionCalled")
    fun `should register condition when condition function called`() {
        val scope = TestConditionDslScope()

        scope.condition("COND_1") { predicate { true } }

        assertNotNull(scope.registeredCondition)
        assertEquals("COND_1", scope.registeredCondition!!.code)
    }

    @Test
    @DisplayName("shouldReturnConditionDefinitionFromConditionFunction")
    fun `should return condition definition from condition function`() {
        val scope = TestConditionDslScope()

        val result = scope.condition("COND_1") { predicate { true } }

        assertSame(scope.registeredCondition, result)
    }

    @Test
    @DisplayName("shouldThrowWhenMultipleConditionBlocksDefined")
    fun `should throw when multiple condition blocks defined`() {
        val scope = TestConditionDslScope()
        scope.condition("COND_1") { predicate { true } }

        val exception =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                scope.condition("COND_2") { predicate { false } }
            }
        assertTrue(exception.message!!.contains("Only one condition block"))
    }

    @Test
    @DisplayName("shouldThrowWhenPredicateBlockMissing")
    fun `should throw when predicate block missing`() {
        val builder = ConditionBuilder("NO_PRED")

        val exception =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                builder.predicate
            }
        assertTrue(exception.message!!.contains("has no predicate block defined"))
    }

    @Test
    @DisplayName("shouldEvaluatePredicateWhenInvoked")
    fun `should evaluate predicate when invoked`() {
        val scope = TestConditionDslScope()
        val cond = scope.condition("COND_1") { predicate { true } }

        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        assertTrue(cond.predicate(ctx))
    }

    private class TestConditionDslScope : ConditionDslScope()
}
