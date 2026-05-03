package cbs.dsl.impl

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperInput
import cbs.dsl.api.HelperOutput
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionContext
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.TransactionBuilder
import cbs.dsl.runtime.helper
import cbs.dsl.runtime.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ImplRegistryTest {
    private lateinit var registry: ImplRegistry

    @BeforeEach
    fun setUp() {
        registry = ImplRegistry()
    }

    @Test
    @DisplayName("shouldResolveTransactionByCodeWhenRegistered")
    fun `shouldResolveTransactionByCodeWhenRegistered`() {
        val tx = TestTransaction("TX_A", executeBlock = {})
        registry.register(tx)

        val resolved = registry.resolveTransaction("TX_A")

        assertSame(tx, resolved)
    }

    @Test
    @DisplayName("shouldResolveTransactionByNameWhenNameSet")
    fun `shouldResolveTransactionByNameWhenNameSet`() {
        val tx = TestTransaction("TX_A", "MyTx", executeBlock = {})
        registry.register(tx)

        val resolved = registry.resolveTransaction("MyTx")

        assertSame(tx, resolved)
    }

    @Test
    @DisplayName("shouldPreferNameOverCodeWhenBothMatch")
    fun `shouldPreferNameOverCodeWhenBothMatch`() {
        val tx = TestTransaction("TX_A", "TX_A", executeBlock = {})
        registry.register(tx)

        val resolved = registry.resolveTransaction("TX_A")

        assertSame(tx, resolved)
    }

    @Test
    @DisplayName("shouldResolveHelperByCodeWhenRegistered")
    fun `shouldResolveHelperByCodeWhenRegistered`() {
        val helper = TestHelper("H_A", executeBlock = { "result" })
        registry.register(helper)

        val resolved = registry.resolveHelper("H_A")

        assertSame(helper, resolved)
    }

    @Test
    @DisplayName("shouldResolveHelperByNameWhenNameSet")
    fun `shouldResolveHelperByNameWhenNameSet`() {
        val helper = TestHelper("H_A", "MyHelper", executeBlock = { "result" })
        registry.register(helper)

        val resolved = registry.resolveHelper("MyHelper")

        assertSame(helper, resolved)
    }

    @Test
    @DisplayName("shouldReturnNullWhenKeyNotFound")
    fun `shouldReturnNullWhenKeyNotFound`() {
        val tx = TestTransaction("TX_A", executeBlock = {})
        val helper = TestHelper("H_A", executeBlock = { "result" })
        registry.register(tx)
        registry.register(helper)

        val txResult = registry.resolveTransaction("UNKNOWN")
        val helperResult = registry.resolveHelper("UNKNOWN")

        assertNull(txResult)
        assertNull(helperResult)
    }

    @Test
    @DisplayName("shouldOverwriteExistingEntryWhenRegisteredAgain")
    fun `shouldOverwriteExistingEntryWhenRegisteredAgain`() {
        val tx1 = TestTransaction("TX_A", executeBlock = {})
        val tx2 = TestTransaction("TX_A", executeBlock = {})
        registry.register(tx1)
        registry.register(tx2)

        val resolved = registry.resolveTransaction("TX_A")

        assertSame(tx2, resolved)
    }

    @Test
    @DisplayName("shouldPopulateFromDslRegistryWhenPopulateFromCalled")
    fun `shouldPopulateFromDslRegistryWhenPopulateFromCalled`() {
        val dslRegistry = DslRegistry()
        val tx = transaction("TX_A") { name("MyTx") }
        val helper = helper("H_A") { name("MyHelper") }
        dslRegistry.register(tx)
        dslRegistry.register(helper)

        registry.populateFrom(dslRegistry)

        val txResolved = registry.resolveTransaction("TX_A")
        val txResolvedByName = registry.resolveTransaction("MyTx")
        val helperResolved = registry.resolveHelper("H_A")
        val helperResolvedByName = registry.resolveHelper("MyHelper")

        assertSame(tx, txResolved)
        assertSame(tx, txResolvedByName)
        assertSame(helper, helperResolved)
        assertSame(helper, helperResolvedByName)
    }
}
