package cbs.dsl.api

import cbs.dsl.api.context.MassOperationContext

interface MassOperationDefinition {
  val code: String
  val parameters: List<ParameterDefinition>
  val category: String
  val triggers: List<TriggerDefinition>
  val source: SourceDefinition
  val lock: LockDefinition?
  val contextBlock: (MassOperationContext) -> Unit
  val itemBlock: (MassOperationContext) -> Unit
  val onPartial: ((Signal) -> Unit)?
  val onCompleted: ((Signal) -> Unit)?
}
