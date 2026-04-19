package cbs.dsl.api.context

import cbs.dsl.api.TransactionDefinition

interface TransactionsScope {
  suspend fun step(tx: TransactionDefinition): StepHandle
  suspend fun step(block: ConditionalStepBuilder.() -> Unit): StepHandle
  suspend fun await(vararg handles: StepHandle)
  operator fun get(key: String): Any?
  operator fun set(key: String, value: Any)
}
