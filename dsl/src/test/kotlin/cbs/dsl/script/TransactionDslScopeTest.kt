package cbs.dsl.script

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.TransactionTypes
import cbs.dsl.api.TransactionTypes.TransactionInput
import cbs.dsl.api.TransactionTypes.TransactionOutput
import cbs.dsl.runtime.TransactionBuilder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TransactionDslScopeTest {
  @Test
  @DisplayName("shouldRegisterTransactionWhenTransactionFunctionCalled")
  fun `should register transaction when transaction function called`() {
    val scope = TestTransactionDslScope()

    val tx = scope.transaction("TX_1") { execute {} }

    assertNotNull(tx)
    assertEquals("TX_1", tx.code)
    assertNotNull(scope.registeredTransaction)
    assertEquals(tx, scope.registeredTransaction)
  }

  @Test
  @DisplayName("shouldBuildTransactionWithPreviewExecuteRollback")
  fun `should build transaction with preview execute rollback`() {
    val scope = TestTransactionDslScope()
    val tx =
        scope.transaction("TX_FULL") {
          preview {}
          execute {}
          rollback {}
        }

    assertNotNull(tx)
    val input = TransactionInput(emptyMap(), "EVT", "1")
    tx.preview(input)
    tx.execute(input)
    tx.rollback(input)
  }

  @Test
  @DisplayName("shouldThrowWhenExecuteBlockMissing")
  fun `should throw when execute block missing`() {
    val scope = TestTransactionDslScope()
    val tx = scope.transaction("TX_NO_EXEC") { preview {} }

    val input = TransactionInput(emptyMap(), "EVT", "1")
    val exception = assertThrows<IllegalStateException> { tx.execute(input) }
    assertTrue(exception.message!!.contains("has no execute block defined"))
  }

  @Test
  @DisplayName("shouldThrowWhenMultipleTransactionBlocksDefined")
  fun `should throw when multiple transaction blocks defined`() {
    val scope = TestTransactionDslScope()
    scope.transaction("TX_1") { execute {} }

    val exception =
      assertThrows<IllegalArgumentException> {
        scope.transaction("TX_2") { execute {} }
      }
    assertTrue(exception.message!!.contains("Only one transaction block is allowed"))
  }

  @Test
  @DisplayName("shouldBeNoOpWhenDelegateCalledWithoutTarget")
  fun `should be no-op when delegate called without target`() {
    val scope = TestTransactionDslScope()
    val tx =
        scope.transaction("TX_NO_TARGET") { execute { ctx -> ctx.delegate() } }
            as TransactionBuilder

    val input = TransactionInput(emptyMap(), "EVT", "1")
    tx.execute(input)
  }

  @Test
  @DisplayName("shouldAllowDelegateCallInRollback")
  fun `should allow delegate call in rollback`() {
    val scope = TestTransactionDslScope()
    var delegateCalled = false

    val baseTx =
        object : TransactionDefinition {
          override fun getCode(): String = "BASE"

          override fun preview(input: TransactionInput) = TransactionOutput.empty()

          override fun execute(input: TransactionInput) = TransactionOutput.empty()

          override fun rollback(input: TransactionInput): TransactionOutput {
            delegateCalled = true
            return TransactionOutput.empty()
          }
        }

    val tx =
        scope.transaction("TX_WITH_DELEGATE") {
          execute {}
          rollback { ctx -> ctx.delegate() }
        } as TransactionBuilder
    tx.delegateTarget = baseTx

    val input = TransactionInput(emptyMap(), "EVT", "1")
    tx.rollback(input)

    assertTrue(
        delegateCalled,
        "Base transaction rollback should have been called via ctx.delegate()",
    )
  }

  @Test
  @DisplayName("shouldAllowDelegateCallInPreview")
  fun `should allow delegate call in preview`() {
    val scope = TestTransactionDslScope()
    var delegateCalled = false

    val baseTx =
        object : TransactionDefinition {
          override fun getCode(): String = "BASE"

          override fun preview(input: TransactionInput): TransactionOutput {
            delegateCalled = true
            return TransactionOutput.empty()
          }

          override fun execute(input: TransactionInput) = TransactionOutput.empty()

          override fun rollback(input: TransactionInput) = TransactionOutput.empty()
        }

    val tx =
        scope.transaction("TX_WITH_DELEGATE_PREVIEW") {
          preview { ctx -> ctx.delegate() }
          execute {}
        } as TransactionBuilder
    tx.delegateTarget = baseTx

    val input = TransactionInput(emptyMap(), "EVT", "1")
    tx.preview(input)

    assertTrue(
        delegateCalled,
        "Base transaction preview should have been called via ctx.delegate()",
    )
  }

  @Test
  @DisplayName("shouldCollectParametersWhenParametersBlockDefined")
  fun `shouldCollectParametersWhenParametersBlockDefined`() {
    val scope = TestTransactionDslScope()

    val tx =
        scope.transaction("TX_WITH_PARAMS") {
          parameters {
            required("param1")
            optional("param2")
          }
          execute {}
        }

    assertEquals(2, tx.parameters.size)
    assertEquals("param1", tx.parameters[0].name)
    assertTrue(tx.parameters[0].required)
    assertEquals("param2", tx.parameters[1].name)
    assertTrue(!tx.parameters[1].required)
  }

  @Test
  @DisplayName("shouldRunContextBlockBeforeExecute")
  fun `shouldRunContextBlockBeforeExecute`() {
    val scope = TestTransactionDslScope()

    val tx =
        scope.transaction("TX_WITH_CONTEXT") {
          context { ctx -> ctx["enriched"] = true }
          execute { ctx -> ctx.enrichment["enriched"] as Boolean }
        }

    val input = TransactionInput(emptyMap(), "EVT", "1")
    val output = tx.execute(input)

    assertTrue(
        output.result["enriched"] as Boolean,
        "Context should have been enriched with 'enriched' key",
    )
  }

  private class TestTransactionDslScope : TransactionDslScope()
}
