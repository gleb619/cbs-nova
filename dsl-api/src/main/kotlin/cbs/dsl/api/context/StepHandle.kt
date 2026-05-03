package cbs.dsl.api.context

import cbs.dsl.api.TransactionDefinition

interface StepHandle {
  suspend fun then(tx: TransactionDefinition): StepHandle
  suspend fun join()
}
