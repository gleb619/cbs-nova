package cbs.dsl.api;

import java.util.List;

/**
 * Defines a workflow — a state machine composed of states, actions, and transitions.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code workflow { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface WorkflowDefinition {

  /** Canonical code used to look up this workflow in the registry. */
  String getCode();

  /** All states in this workflow. */
  List<String> getStates();

  /** The initial state when a workflow instance is created. */
  String getInitial();

  /** States that terminate the workflow. */
  List<String> getTerminalStates();

  /** Transition rules governing state changes. */
  List<TransitionRuleDefinition> getTransitions();

  /** Executes a state transition based on the current state and action. */
  WorkflowOutput execute(WorkflowInput input);
}
