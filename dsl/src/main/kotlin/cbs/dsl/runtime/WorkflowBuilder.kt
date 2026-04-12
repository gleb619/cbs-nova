package cbs.dsl.runtime

import cbs.dsl.api.Action
import cbs.dsl.api.EventDefinition
import cbs.dsl.api.TransitionRule
import cbs.dsl.api.WorkflowDefinition

class WorkflowBuilder(override val code: String) : WorkflowDefinition {
  private val _states = mutableListOf<String>()
  override val states: List<String> get() = _states.toList()

  private var _initial: String = ""
  override val initial: String get() = _initial

  private val _terminalStates = mutableListOf<String>()
  override val terminalStates: List<String> get() = _terminalStates.toList()

  private val _transitions = mutableListOf<TransitionRule>()
  override val transitions: List<TransitionRule> get() = _transitions.toList()

  fun states(vararg s: String) {
    _states.addAll(s)
  }

  fun initial(s: String) {
    _initial = s
  }

  fun terminalStates(vararg s: String) {
    _terminalStates.addAll(s)
  }

  fun transition(
    from: String,
    to: String,
    on: Action,
    event: EventDefinition,
    onFault: String = "FAULTED",
  ) {
    _transitions += TransitionRule(from, to, on, event, onFault)
  }
}

fun workflow(code: String, block: WorkflowBuilder.() -> Unit): WorkflowDefinition =
  WorkflowBuilder(code).apply(block)
