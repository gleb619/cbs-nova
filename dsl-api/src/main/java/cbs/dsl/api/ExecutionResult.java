package cbs.dsl.api;

import lombok.Builder;

@Builder
public record ExecutionResult(String status, long eventExecutionId, long workflowExecutionId) {}
