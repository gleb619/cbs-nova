package cbs.dsl.api;

public record ExecutionResult(
  String status,
  long eventExecutionId,
  long workflowExecutionId
) {
}
