package cbs.dsl.script

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionContext
import cbs.dsl.runtime.TransactionBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransactionDslScopeTest {
    @Test
    @DisplayName("shouldRegisterTransactionWhenTransactionFunctionCalled")
    fun `should register transaction when transaction function called`() {
        val scope = TestTransactionDslScope()

        val tx =
            scope.transaction("TX_1") {
                execute { }
            }

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
                preview { }
                execute { }
                rollback { }
            }

        assertNotNull(tx)
        // Check if internal fields are set (via reflection or by just executing them)
        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        tx.preview(ctx)
        tx.execute(ctx)
        tx.rollback(ctx)
    }

    @Test
    @DisplayName("shouldThrowWhenExecuteBlockMissing")
    fun `should throw when execute block missing`() {
        val scope = TestTransactionDslScope()
        val tx =
            scope.transaction("TX_NO_EXEC") {
                preview { }
            }

        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        val exception =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                tx.execute(ctx)
            }
        assertTrue(exception.message!!.contains("has no execute block defined"))
    }

    @Test
    @DisplayName("shouldThrowWhenMultipleTransactionBlocksDefined")
    fun `should throw when multiple transaction blocks defined`() {
        val scope = TestTransactionDslScope()
        scope.transaction("TX_1") { execute { } }

        val exception =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                scope.transaction("TX_2") { execute { } }
            }
        assertTrue(exception.message!!.contains("Only one transaction block is allowed"))
    }

    @Test
    @DisplayName("shouldBeNoOpWhenDelegateCalledWithoutTarget")
    fun `should be no-op when delegate called without target`() {
        val scope = TestTransactionDslScope()
        val tx =
            scope.transaction("TX_NO_TARGET") {
                execute { ctx -> ctx.delegate() }
            } as TransactionBuilder

        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        // Should not throw
        tx.execute(ctx)
    }

    @Test
    @DisplayName("shouldAllowDelegateCallInRollback")
    fun `should allow delegate call in rollback`() {
        val scope = TestTransactionDslScope()
        var delegateCalled = false

        val baseTx =
            object : TransactionDefinition {
                override val code: String = "BASE"

                override fun preview(ctx: TransactionContext) {}

                override fun execute(ctx: TransactionContext) {}

                override fun rollback(ctx: TransactionContext) {
                    delegateCalled = true
                }
            }

        val tx =
            scope.transaction("TX_WITH_DELEGATE") {
                execute { }
                rollback { ctx ->
                    ctx.delegate()
                }
            } as TransactionBuilder
        tx.delegateTarget = baseTx

        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        tx.rollback(ctx)

        assertTrue(delegateCalled, "Base transaction rollback should have been called via ctx.delegate()")
    }

    @Test
    @DisplayName("shouldAllowDelegateCallInPreview")
    fun `should allow delegate call in preview`() {
        val scope = TestTransactionDslScope()
        var delegateCalled = false

        val baseTx =
            object : TransactionDefinition {
                override val code: String = "BASE"

                override fun preview(ctx: TransactionContext) {
                    delegateCalled = true
                }

                override fun execute(ctx: TransactionContext) {}

                override fun rollback(ctx: TransactionContext) {}
            }

        val tx =
            scope.transaction("TX_WITH_DELEGATE_PREVIEW") {
                preview { ctx ->
                    ctx.delegate()
                }
                execute { }
            } as TransactionBuilder
        tx.delegateTarget = baseTx

        val ctx = TransactionContext("EVT", 1L, "user", "v1", emptyMap(), false)
        tx.preview(ctx)

        assertTrue(delegateCalled, "Base transaction preview should have been called via ctx.delegate()")
    }

    private class TestTransactionDslScope : TransactionDslScope()
}
