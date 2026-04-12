package cbs.dsl.api

interface WorkflowDefinition {
  val code: String
  val states: List<String>
  val initial: String
  val terminalStates: List<String>
  val transitions: List<TransitionRule>
}
