package cbs.nova.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record WorkflowExecutionResponse(Long executionId, String status) {}
