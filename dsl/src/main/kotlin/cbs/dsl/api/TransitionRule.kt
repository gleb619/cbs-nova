package cbs.dsl.api

data class TransitionRule(
  val from: String,
  val to: String,
  val on: Action,
  val event: EventDefinition,
  val onFault: String = "FAULTED",
)
