package cbs.dsl.runtime

import cbs.dsl.api.Action
import cbs.dsl.api.EventDefinition
import cbs.dsl.api.TransitionRuleDefinition
import cbs.dsl.api.WorkflowDefinition
import cbs.dsl.api.WorkflowTypes.WorkflowInput
import cbs.dsl.api.WorkflowTypes.WorkflowOutput

class WorkflowBuilder(val workflowCode: String) : WorkflowDefinition {
  private val _states = mutableListOf<String>()
  private var _initial: String = ""
  private val _terminalStates = mutableListOf<String>()
  private val _transitions = mutableListOf<TransitionRuleDefinition>()

  override fun getCode(): String = workflowCode

  override fun getTransitions(): List<TransitionRuleDefinition> = _transitions.toList()

  fun states(vararg s: String) {
    _states.addAll(s)
  }

  fun initial(s: String) {
    _initial = s
  }

  fun terminalStates(vararg s: String) {
    _terminalStates.addAll(s)
  }

  fun transitions(block: TransitionScope.() -> Unit) {
    val scope = TransitionScope()
    scope.block()
    _transitions += scope.rules
  }

  @Deprecated(
      message = "Use transitions { } block with closure syntax instead",
      level = DeprecationLevel.WARNING,
  )
  fun transition(
      from: String,
      to: String,
      on: Action,
      event: EventDefinition,
      onFault: String = "FAULTED",
  ) {
    _transitions += TransitionRuleDefinition(from, to, on, event, onFault)
  }

  // Auto-inference methods
  private fun inferredStates(): List<String> {
    if (_states.isNotEmpty()) return _states
    return (_transitions.map { it.from } + _transitions.map { it.to }).distinct()
  }

  private fun inferredInitial(): String {
    if (_initial.isNotEmpty()) return _initial
    val targets = _transitions.map { it.to }.toSet()
    return inferredStates().firstOrNull { it !in targets } ?: ""
  }

  private fun inferredTerminal(): List<String> {
    if (_terminalStates.isNotEmpty()) return _terminalStates
    val sources = _transitions.map { it.from }.toSet()
    return inferredStates().filter { it !in sources }
  }

  override fun getStates(): List<String> = inferredStates()

  override fun getInitial(): String = inferredInitial()

  override fun getTerminalStates(): List<String> = inferredTerminal()

  override fun execute(input: WorkflowInput): WorkflowOutput {
    val rule =
        transitions.find { it.from == input.currentState && it.on.name == input.action }
            ?: error("No transition from ${input.currentState} on ${input.action}")

    return WorkflowOutput(rule.to, listOf(rule.event.code), "SUCCESS")
  }
}

fun workflow(code: String, block: WorkflowBuilder.() -> Unit): WorkflowDefinition =
    WorkflowBuilder(code).apply(block)
