package cbs.dsl.api.context

open class FinishContext(
  eventCode: String,
  workflowExecutionId: Long,
  performedBy: String,
  dslVersion: String,
  eventParameters: Map<String, Any>,
) : EnrichmentContext(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters)
