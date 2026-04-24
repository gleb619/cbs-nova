package cbs.dsl.api.context

open class TransactionContext(
  eventCode: String,
  workflowExecutionId: Long,
  performedBy: String,
  dslVersion: String,
  eventParameters: Map<String, Any>,
  val isResumed: Boolean,
) : EnrichmentContext(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters) {

  open fun delegate(): Unit = Unit
}
