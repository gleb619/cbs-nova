package cbs.dsl.script

import cbs.dsl.api.context.TransactionContext
import cbs.dsl.runtime.ConditionBuilder
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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

    val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
    val exception =
        org.junit.jupiter.api.assertThrows<IllegalStateException> { builder.predicate.test(ctx) }
    assertTrue(exception.message!!.contains("has no predicate block defined"))
  }

  @Test
  @DisplayName("shouldEvaluatePredicateWhenInvoked")
  fun `should evaluate predicate when invoked`() {
    val scope = TestConditionDslScope()
    val cond = scope.condition("COND_1") { predicate { true } }

    val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
    assertTrue(cond.predicate.test(ctx))
  }

  @Test
  @DisplayName("shouldCollectParametersWhenParametersBlockDefined")
  fun `shouldCollectParametersWhenParametersBlockDefined`() {
    val scope = TestConditionDslScope()
    val cond =
        scope.condition("COND_WITH_PARAMS") {
          parameters {
            required("param1")
            optional("param2")
          }
          predicate { true }
        }

    assertEquals(2, cond.parameters.size)
    assertEquals("param1", cond.parameters[0].name)
    assertTrue(cond.parameters[0].required)
    assertEquals("param2", cond.parameters[1].name)
    assertTrue(!cond.parameters[1].required)
  }

  @Test
  @DisplayName("shouldRunContextBlockBeforePredicate")
  fun `shouldRunContextBlockBeforePredicate`() {
    val scope = TestConditionDslScope()
    val cond =
        scope.condition("COND_WITH_CONTEXT") {
          context { ctx -> ctx["enriched"] = true }
          predicate { ctx -> ctx.enrichment["enriched"] == true }
        }

    val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
    assertTrue(cond.predicate.test(ctx))
  }

  private class TestConditionDslScope : ConditionDslScope()
}
