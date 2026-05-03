package cbs.dsl.api;

import lombok.Builder;

@Builder(toBuilder = true)
public record ExecutionResult(String status, long eventExecutionId, long workflowExecutionId) {}
