package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.TransactionInput
import cbs.dsl.api.TransactionOutput
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ConditionalStepBuilderTest {
  private val testTransaction =
      object : TransactionDefinition {
        override val code: String = "TEST_TX"

        override fun preview(input: TransactionInput) = TransactionOutput()

        override fun execute(input: TransactionInput) = TransactionOutput()

        override fun rollback(input: TransactionInput) = TransactionOutput()
      }

  private val anotherTransaction =
      object : TransactionDefinition {
        override val code: String = "ANOTHER_TX"

        override fun preview(input: TransactionInput) = TransactionOutput()

        override fun execute(input: TransactionInput) = TransactionOutput()

        override fun rollback(input: TransactionInput) = TransactionOutput()
      }

  private val fallbackTransaction =
      object : TransactionDefinition {
        override val code: String = "FALLBACK_TX"

        override fun preview(input: TransactionInput) = TransactionOutput()

        override fun execute(input: TransactionInput) = TransactionOutput()

        override fun rollback(input: TransactionInput) = TransactionOutput()
      }

  @Test
  @DisplayName("shouldSelectThenBranchWhenPredicateTrue")
  fun `should select then branch when predicate true`() = runBlocking {
    val builder = ConditionalStepBuilderImpl()

    builder.apply {
      `when`({ true }) then
          {
            transaction(testTransaction)
          } otherwise
          {
            transaction(fallbackTransaction)
          }
    }

    val node = builder.build()
    assertTrue(node is StepNode.Conditional)

    val conditional = node as StepNode.Conditional
    assertEquals(1, conditional.branches.size)
    assertNotNull(conditional.otherwise)
    assertEquals(testTransaction, (conditional.branches[0].node as StepNode.Direct).tx)
    assertEquals(fallbackTransaction, (conditional.otherwise as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldSelectOrWhenBranchWhenFirstPredicateFalse")
  fun `should select orWhen branch when first predicate false`() = runBlocking {
    val builder = ConditionalStepBuilderImpl()

    builder.apply {
      `when`({ false }) then
          {
            transaction(testTransaction)
          } orWhen
          ({
            true
          }) then
          {
            transaction(anotherTransaction)
          } otherwise
          {
            transaction(fallbackTransaction)
          }
    }

    val node = builder.build()
    assertTrue(node is StepNode.Conditional)

    val conditional = node as StepNode.Conditional
    assertEquals(2, conditional.branches.size)
    assertNotNull(conditional.otherwise)
    assertEquals(testTransaction, (conditional.branches[0].node as StepNode.Direct).tx)
    assertEquals(anotherTransaction, (conditional.branches[1].node as StepNode.Direct).tx)
    assertEquals(fallbackTransaction, (conditional.otherwise as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldSelectOtherwiseWhenAllPredicatesFalse")
  fun `should select otherwise when all predicates false`() = runBlocking {
    val builder = ConditionalStepBuilderImpl()

    builder.apply {
      `when`({ false }) then
          {
            transaction(testTransaction)
          } orWhen
          ({
            false
          }) then
          {
            transaction(anotherTransaction)
          } otherwise
          {
            transaction(fallbackTransaction)
          }
    }

    val node = builder.build()
    assertTrue(node is StepNode.Conditional)

    val conditional = node as StepNode.Conditional
    assertEquals(2, conditional.branches.size)
    assertNotNull(conditional.otherwise)
    assertEquals(testTransaction, (conditional.branches[0].node as StepNode.Direct).tx)
    assertEquals(anotherTransaction, (conditional.branches[1].node as StepNode.Direct).tx)
    assertEquals(fallbackTransaction, (conditional.otherwise as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldBuildConditionalWithoutOtherwise")
  fun `should build conditional without otherwise`() = runBlocking {
    val builder = ConditionalStepBuilderImpl()

    builder.apply { `when`({ true }) then { transaction(testTransaction) } }

    val node = builder.build()
    assertTrue(node is StepNode.Conditional)

    val conditional = node as StepNode.Conditional
    assertEquals(1, conditional.branches.size)
    assertEquals(null, conditional.otherwise)
    assertEquals(testTransaction, (conditional.branches[0].node as StepNode.Direct).tx)
  }
}
