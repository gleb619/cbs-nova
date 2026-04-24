package cbs.dsl.runtime

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import cbs.dsl.api.context.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DslRegistryTest {
  @Test
  fun `shouldRegisterAndRetrieveWorkflow`() {
    val registry = DslRegistry()
    val wf =
        workflow("test-wf") {
          states("INIT", "DONE")
          initial("INIT")
          terminalStates("DONE")
        }

    registry.register(wf)

    assertEquals(1, registry.workflows.size)
    assertEquals(wf, registry.workflows["test-wf"])
  }

  @Test
  fun `shouldThrowOnDuplicateWorkflowCode`() {
    val registry = DslRegistry()
    val wf =
        workflow("test-wf") {
          states("INIT", "DONE")
          initial("INIT")
          terminalStates("DONE")
        }

    registry.register(wf)

    val exception = assertFailsWith<IllegalArgumentException> { registry.register(wf) }
    assertTrue(exception.message!!.contains("Duplicate registration for code 'test-wf'"))
  }

  @Test
  fun `shouldRegisterAllDefinitionTypes`() {
    val registry = DslRegistry()

    val wf =
        workflow("wf-1") {
          states("INIT", "DONE")
          initial("INIT")
          terminalStates("DONE")
        }

    val evt = event("evt-1") {}

    val tx = transaction("tx-1") { execute {} }

    val massOp =
        massOperation("mass-1") {
          category("BATCH")
          item {}
        }

    val helper =
        object : HelperDefinition {
          override val code: String = "helper-1"

          override fun execute(input: HelperInput): HelperOutput = HelperOutput(Unit)
        }

    val condition =
        object : ConditionDefinition {
          override val code: String = "cond-1"
          override val predicate: (TransactionContext) -> Boolean = { true }
        }

    registry.register(wf)
    registry.register(evt)
    registry.register(tx)
    registry.register(massOp)
    registry.register(helper)
    registry.register(condition)

    assertEquals(1, registry.workflows.size)
    assertEquals(1, registry.events.size)
    assertEquals(1, registry.transactions.size)
    assertEquals(1, registry.massOperations.size)
    assertEquals(1, registry.helpers.size)
    assertEquals(1, registry.conditions.size)
  }
}
