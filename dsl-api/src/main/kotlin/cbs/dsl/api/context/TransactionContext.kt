package cbs.dsl.api.context

import cbs.dsl.api.Action

open class TransactionContext(
  eventCode: String,
  workflowExecutionId: Long,
  performedBy: String,
  dslVersion: String,
  eventParameters: Map<String, Any>,
  val isResumed: Boolean,
) : EnrichmentContext(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters) {
  fun prolong(action: Action): Unit = Unit

  open fun delegate(): Unit = Unit
}
