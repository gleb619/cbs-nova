package cbs.dsl.runtime

import cbs.dsl.api.Action
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowBuilderTest {

  @Test
  @DisplayName("shouldBuildTransitionsWithClosureSyntax")
  fun shouldBuildTransitionsWithClosureSyntax() {
    val wf = workflow("test-wf") {
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {
          // event block
        } onFault {
          // fault block
        }
        ("ENTERED" to "APPROVED" on Action.APPROVE) {
          // event block
        }
      }
    }

    assertEquals("test-wf", wf.code)
    assertEquals(2, wf.transitions.size)

    val t1 = wf.transitions[0]
    assertEquals("DRAFT", t1.from)
    assertEquals("ENTERED", t1.to)
    assertEquals(Action.SUBMIT, t1.on)
    assertEquals("FAULTED", t1.onFault)
    assertTrue(t1.onFaultBlock != null)

    val t2 = wf.transitions[1]
    assertEquals("ENTERED", t2.from)
    assertEquals("APPROVED", t2.to)
    assertEquals(Action.APPROVE, t2.on)
    assertEquals("FAULTED", t2.onFault)
    assertTrue(t2.onFaultBlock == null)
  }

  @Test
  @DisplayName("shouldInferStatesFromTransitions")
  fun shouldInferStatesFromTransitions() {
    val wf = workflow("test-wf") {
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {}
        ("ENTERED" to "APPROVED" on Action.APPROVE) {}
        ("APPROVED" to "COMPLETED" on Action.CLOSE) {}
      }
    }

    assertEquals(listOf("DRAFT", "ENTERED", "APPROVED", "COMPLETED"), wf.states)
  }

  @Test
  @DisplayName("shouldInferInitialAsStateNeverATarget")
  fun shouldInferInitialAsStateNeverATarget() {
    val wf = workflow("test-wf") {
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {}
        ("ENTERED" to "APPROVED" on Action.APPROVE) {}
      }
    }

    assertEquals("DRAFT", wf.initial)
  }

  @Test
  @DisplayName("shouldInferTerminalAsStateNeverASource")
  fun shouldInferTerminalAsStateNeverASource() {
    val wf = workflow("test-wf") {
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {}
        ("ENTERED" to "APPROVED" on Action.APPROVE) {}
      }
    }

    assertEquals(listOf("APPROVED"), wf.terminalStates)
  }

  @Test
  @DisplayName("shouldSupportOnFaultClosure")
  fun shouldSupportOnFaultClosure() {
    var faultBlockExecuted = false

    val wf = workflow("test-wf") {
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {} onFault {
          faultBlockExecuted = true
        }
      }
    }

    assertEquals(1, wf.transitions.size)
    val transition = wf.transitions[0]
    assertTrue(transition.onFaultBlock != null)
  }

  @Test
  @DisplayName("shouldRespectExplicitStatesOverInference")
  fun shouldRespectExplicitStatesOverInference() {
    val wf = workflow("test-wf") {
      states("CUSTOM1", "CUSTOM2")
      initial("CUSTOM1")
      terminalStates("CUSTOM2")
      transitions {
        ("DRAFT" to "ENTERED" on Action.SUBMIT) {}
      }
    }

    assertEquals(listOf("CUSTOM1", "CUSTOM2"), wf.states)
    assertEquals("CUSTOM1", wf.initial)
    assertEquals(listOf("CUSTOM2"), wf.terminalStates)
  }
}
