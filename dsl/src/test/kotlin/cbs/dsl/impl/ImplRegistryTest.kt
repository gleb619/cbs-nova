package cbs.dsl.impl

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.helper
import cbs.dsl.runtime.transaction
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImplRegistryTest {
  private lateinit var registry: ImplRegistry

  @BeforeEach
  fun setUp() {
    registry = ImplRegistry()
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

  @Test
  @DisplayName("shouldPopulateHelperParametersAndContextBlockFromDslRegistry")
  fun `shouldPopulateHelperParametersAndContextBlockFromDslRegistry`() {
    val dslRegistry = DslRegistry()
    val helper =
        helper("H_PARAMS") {
          parameters {
            required("param1")
            optional("param2")
          }
          context { ctx -> ctx["enriched"] = true }
          name("HelperWithParams")
        }
    dslRegistry.register(helper)

    registry.populateFrom(dslRegistry)

    val resolved = registry.resolveHelper("H_PARAMS") as HelperDefinition

    assertEquals(2, resolved.parameters.size)
    assertEquals("param1", resolved.parameters[0].name)
    kotlin.test.assertTrue(resolved.parameters[0].required)
    assertEquals("param2", resolved.parameters[1].name)
    kotlin.test.assertTrue(!resolved.parameters[1].required)
    assertNotNull(resolved.contextBlock)
  }

  @Test
  @DisplayName("shouldPopulateTransactionParametersAndContextBlockFromDslRegistry")
  fun `shouldPopulateTransactionParametersAndContextBlockFromDslRegistry`() {
    val dslRegistry = DslRegistry()
    val tx =
        transaction("TX_PARAMS") {
          parameters {
            required("txParam1")
            optional("txParam2")
          }
          context { ctx -> ctx["txEnriched"] = true }
          name("TransactionWithParams")
          execute {}
        }
    dslRegistry.register(tx)

    registry.populateFrom(dslRegistry)

    val resolved = registry.resolveTransaction("TX_PARAMS") as TransactionDefinition

    assertEquals(2, resolved.parameters.size)
    assertEquals("txParam1", resolved.parameters[0].name)
    kotlin.test.assertTrue(resolved.parameters[0].required)
    assertEquals("txParam2", resolved.parameters[1].name)
    kotlin.test.assertTrue(!resolved.parameters[1].required)
    assertNotNull(resolved.contextBlock)
  }

  @Test
  @DisplayName("shouldPopulateConditionParametersAndContextBlockFromDslRegistry")
  fun `shouldPopulateConditionParametersAndContextBlockFromDslRegistry`() {
    val dslRegistry = DslRegistry()
    val condition =
        cbs.dsl.runtime.ConditionBuilder("COND_PARAMS").apply {
          parameters {
            required("condParam1")
            optional("condParam2")
          }
          context { ctx -> ctx["condEnriched"] = true }
          predicate { ctx -> true }
        }
    dslRegistry.register(condition)

    registry.populateFrom(dslRegistry)

    val resolved = registry.resolveCondition("COND_PARAMS") as ConditionDefinition

    assertEquals(2, resolved.parameters.size)
    assertEquals("condParam1", resolved.parameters[0].name)
    kotlin.test.assertTrue(resolved.parameters[0].required)
    assertEquals("condParam2", resolved.parameters[1].name)
    kotlin.test.assertTrue(!resolved.parameters[1].required)
    assertNotNull(resolved.contextBlock)
  }
}
