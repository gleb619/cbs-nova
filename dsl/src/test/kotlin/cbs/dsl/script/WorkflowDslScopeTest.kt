package cbs.dsl.script

import cbs.dsl.api.Action
import cbs.dsl.api.WorkflowDefinition
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkflowDslScopeTest {
  @Test
  @DisplayName("shouldRegisterWorkflowWhenWorkflowFunctionCalled")
  fun `should register workflow when workflow function called`() {
    val scope = TestWorkflowDslScope()

    val workflow =
        scope.workflow("TEST_WF") {
          // Workflow definition block
        }

    assertNotNull(workflow)
    assertEquals("TEST_WF", workflow.code)
    assertEquals(1, scope.getRegisteredWorkflows().size)
    assertEquals(workflow, scope.getRegisteredWorkflows()[0])
  }

  @Test
  @DisplayName("shouldRegisterMultipleWorkflowsWhenCalledTwice")
  fun `should register multiple workflows when called twice`() {
    val scope = TestWorkflowDslScope()

    val workflow1 =
        scope.workflow("WF_1") {
          // First workflow definition block
        }

    val workflow2 =
        scope.workflow("WF_2") {
          // Second workflow definition block
        }

    assertNotNull(workflow1)
    assertNotNull(workflow2)
    assertEquals("WF_1", workflow1.code)
    assertEquals("WF_2", workflow2.code)
    assertEquals(2, scope.getRegisteredWorkflows().size)
    assertEquals(workflow1, scope.getRegisteredWorkflows()[0])
    assertEquals(workflow2, scope.getRegisteredWorkflows()[1])
  }

  @Test
  @DisplayName("shouldReturnWorkflowDefinitionWithTransitionsFromWorkflowFunction")
  fun `should return workflow definition with transitions from workflow function`() {
    val scope = TestWorkflowDslScope()

    val workflow =
        scope.workflow("TEST_WF") {
          states("DRAFT", "ENTERED", "COMPLETED")
          initial("DRAFT")
          terminalStates("COMPLETED")
          transitions { ("DRAFT" to "ENTERED" on Action.SUBMIT) {} }
        }

    assertNotNull(workflow)
    assertEquals("TEST_WF", workflow.code)
    assertEquals(listOf("DRAFT", "ENTERED", "COMPLETED"), workflow.states)
    assertEquals("DRAFT", workflow.initial)
    assertEquals(listOf("COMPLETED"), workflow.terminalStates)
    assertEquals(1, workflow.transitions.size)
    assertEquals("DRAFT", workflow.transitions[0].from)
    assertEquals("ENTERED", workflow.transitions[0].to)
    assertEquals(Action.SUBMIT, workflow.transitions[0].on)
  }

  @Test
  @DisplayName("should preserve registration order when multiple workflows added")
  fun shouldPreserveRegistrationOrderWhenMultipleWorkflowsAdded() {
    val scope = TestWorkflowDslScope()

    scope.workflow("WF_C") {}
    scope.workflow("WF_A") {}
    scope.workflow("WF_B") {}

    val registered = scope.getRegisteredWorkflows()
    assertEquals(3, registered.size)
    assertEquals("WF_C", registered[0].code)
    assertEquals("WF_A", registered[1].code)
    assertEquals("WF_B", registered[2].code)
  }

  @Test
  @DisplayName("should return same instance in registeredWorkflows and return getValue")
  fun shouldReturnSameInstanceInRegisteredWorkflowsAndReturnValue() {
    val scope = TestWorkflowDslScope()

    val wf = scope.workflow("X") {}

    assertSame(wf, scope.getRegisteredWorkflows()[0])
  }

  // Test implementation of WorkflowDslScope
  private class TestWorkflowDslScope : WorkflowDslScope() {
    // Expose registeredWorkflows for testing
    fun getRegisteredWorkflows(): List<WorkflowDefinition> = registeredWorkflows.toList()
  }
}
