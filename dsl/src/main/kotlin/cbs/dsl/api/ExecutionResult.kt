package cbs.dsl.api

data class ExecutionResult(
  val status: String,
  val eventExecutionId: Long,
  val workflowExecutionId: Long,
)
