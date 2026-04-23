package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TransactionsScopeTest {
  private val testTransaction =
      object : TransactionDefinition {
        override val code: String = "TEST_TX"

        override fun preview(ctx: cbs.dsl.api.context.TransactionContext) {}

        override fun execute(ctx: cbs.dsl.api.context.TransactionContext) {}

        override fun rollback(ctx: cbs.dsl.api.context.TransactionContext) {}
      }

  private val anotherTransaction =
      object : TransactionDefinition {
        override val code: String = "ANOTHER_TX"

        override fun preview(ctx: cbs.dsl.api.context.TransactionContext) {}

        override fun execute(ctx: cbs.dsl.api.context.TransactionContext) {}

        override fun rollback(ctx: cbs.dsl.api.context.TransactionContext) {}
      }

  @Test
  @DisplayName("shouldRecordDirectStepWhenStepCalled")
  fun `should record direct step when step called`() = runBlocking {
    val scope = TransactionsScopeImpl()

    val handle = scope.step(testTransaction)

    assertNotNull(handle)
    assertEquals(1, scope.steps.size)
    assertTrue(scope.steps[0] is StepNode.Direct)
    assertEquals(testTransaction, (scope.steps[0] as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldChainStepsWhenThenCalled")
  fun `should chain steps when then called`() = runBlocking {
    val scope = TransactionsScopeImpl()

    val firstHandle = scope.step(testTransaction)
    val secondHandle = firstHandle.then(anotherTransaction)

    assertNotNull(secondHandle)
    assertEquals(1, scope.steps.size)
    assertTrue(scope.steps[0] is StepNode.Chain)

    val chain = scope.steps[0] as StepNode.Chain
    assertTrue(chain.head is StepNode.Direct)
    assertTrue(chain.tail is StepNode.Direct)
    assertEquals(testTransaction, (chain.head as StepNode.Direct).tx)
    assertEquals(anotherTransaction, (chain.tail as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldRecordBarrierWhenAwaitCalled")
  fun `should record barrier when await called`() = runBlocking {
    val scope = TransactionsScopeImpl()

    val handle1 = scope.step(testTransaction)
    val handle2 = scope.step(anotherTransaction)

    scope.await(handle1, handle2)

    assertEquals(3, scope.steps.size)
    assertTrue(scope.steps[2] is StepNode.Barrier)

    val barrier = scope.steps[2] as StepNode.Barrier
    assertEquals(2, barrier.handles.size)
  }

  @Test
  @DisplayName("shouldRecordConditionalStepWhenStepWithBlockCalled")
  fun `should record conditional step when step with block called`() = runBlocking {
    val scope = TransactionsScopeImpl()

    val handle =
        scope.step {
          `when`({ true }) then
              {
                transaction(testTransaction)
              } otherwise
              {
                transaction(anotherTransaction)
              }
        }

    assertNotNull(handle)
    assertEquals(1, scope.steps.size)
    assertTrue(scope.steps[0] is StepNode.Conditional)

    val conditional = scope.steps[0] as StepNode.Conditional
    assertEquals(1, conditional.branches.size)
    assertNotNull(conditional.otherwise)
    assertEquals(testTransaction, (conditional.branches[0].node as StepNode.Direct).tx)
    assertEquals(anotherTransaction, (conditional.otherwise as StepNode.Direct).tx)
  }

  @Test
  @DisplayName("shouldManageContextMap")
  fun `should manage context map`() = runBlocking {
    val scope = TransactionsScopeImpl()

    scope["testKey"] = "testValue"
    assertEquals("testValue", scope["testKey"])

    scope["numberKey"] = 42
    assertEquals(42, scope["numberKey"])
  }
}
