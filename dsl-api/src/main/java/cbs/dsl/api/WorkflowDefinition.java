package cbs.dsl.api;

import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;

import cbs.dsl.builder.WorkflowDslObject;
import java.util.List;

/**
 * Defines a workflow — a state machine composed of states, actions, and transitions.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code workflow { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface WorkflowDefinition extends DslDefinition<WorkflowDslObject> {

  /**
   * Canonical code used to look up this workflow in the registry.
   *
   * @return the workflow code
   */
  String getCode();

  /**
   * All states in this workflow.
   *
   * @return the states
   */
  List<String> getStates();

  /**
   * The initial state when a workflow instance is created.
   *
   * @return the initial state
   */
  String getInitial();

  /**
   * States that terminate the workflow.
   *
   * @return the terminal states
   */
  List<String> getTerminalStates();

  /**
   * Transition rules governing state changes.
   *
   * @return the transition rules
   */
  List<TransitionRuleDefinition> getTransitions();

  /**
   * Executes a state transition based on the current state and action.
   *
   * @param input the workflow input
   * @return the workflow output
   */
  WorkflowOutput execute(WorkflowInput input);

  default WorkflowOutput preview(WorkflowInput input) {
    return execute(input);
  }

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default WorkflowDslObject dsl() {
    throw new NullPointerException("Dsl object not added");
  }
}
