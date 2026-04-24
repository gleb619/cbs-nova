package cbs.dsl.api.context

open class ParameterContext(
    eventCode: String,
    workflowExecutionId: Long,
    performedBy: String,
    dslVersion: String,
    val eventParameters: Map<String, Any>,
) : BaseContext(eventCode, workflowExecutionId, performedBy, dslVersion)
