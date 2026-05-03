package cbs.dsl.runtime

import cbs.dsl.api.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowBuilderTest {
  @Test
  fun `shouldBuildWorkflowWithAllFields`() {
    val eventDef = event("test-event") {}

    val wf = workflow("test-wf") {
      states("INIT", "PROCESSING", "DONE")
      initial("INIT")
      terminalStates("DONE")
      transition(
        from = "INIT",
        to = "PROCESSING",
        on = Action.SUBMIT,
        event = eventDef,
      )
      transition(
        from = "PROCESSING",
        to = "DONE",
        on = Action.APPROVE,
        event = eventDef,
        onFault = "REJECTED",
      )
    }

    assertEquals("test-wf", wf.code)
    assertEquals(listOf("INIT", "PROCESSING", "DONE"), wf.states)
    assertEquals("INIT", wf.initial)
    assertEquals(listOf("DONE"), wf.terminalStates)
    assertEquals(2, wf.transitions.size)

    val t1 = wf.transitions[0]
    assertEquals("INIT", t1.from)
    assertEquals("PROCESSING", t1.to)
    assertEquals(Action.SUBMIT, t1.on)
    assertEquals("FAULTED", t1.onFault)

    val t2 = wf.transitions[1]
    assertEquals("PROCESSING", t2.from)
    assertEquals("DONE", t2.to)
    assertEquals(Action.APPROVE, t2.on)
    assertEquals("REJECTED", t2.onFault)
  }

  @Test
  fun `shouldDefaultOnFaultToFaulted`() {
    val eventDef = event("test-event") {}

    val wf = workflow("test-wf") {
      states("INIT", "DONE")
      initial("INIT")
      terminalStates("DONE")
      transition(
        from = "INIT",
        to = "DONE",
        on = Action.SUBMIT,
        event = eventDef,
      )
    }

    assertEquals("FAULTED", wf.transitions[0].onFault)
  }
}
