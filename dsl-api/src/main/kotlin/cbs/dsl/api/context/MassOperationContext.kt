package cbs.dsl.api.context

open class MassOperationContext(
  val itemKey: String,
  val itemData: Map<String, Any>,
  val massOpExecutionId: Long,
  val performedBy: String,
  val dslVersion: String,
)
