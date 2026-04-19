package cbs.dsl.api.context

open class EnrichmentContext(
  eventCode: String,
  workflowExecutionId: Long,
  performedBy: String,
  dslVersion: String,
  eventParameters: Map<String, Any>,
) : ParameterContext(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters) {
  val enrichment: MutableMap<String, Any> = mutableMapOf()

  operator fun set(key: String, value: Any) {
    enrichment[key] = value
  }
}
