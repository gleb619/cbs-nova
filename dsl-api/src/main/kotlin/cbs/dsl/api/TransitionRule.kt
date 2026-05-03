package cbs.dsl.api

import cbs.dsl.api.context.TransactionsScope

data class TransitionRule(
  val from: String,
  val to: String,
  val on: Action,
  val event: EventDefinition,
  val onFault: String = "FAULTED",
  val onFaultBlock: (suspend TransactionsScope.() -> Unit)? = null,
)
