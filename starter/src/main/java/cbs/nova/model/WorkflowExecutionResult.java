package cbs.nova.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record WorkflowExecutionResult(Long executionId, String status) {}
