package cbs.dsl.api.context

open class BaseContext(
    val eventCode: String,
    val workflowExecutionId: Long,
    val performedBy: String,
    val dslVersion: String,
)
