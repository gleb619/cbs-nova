package cbs.nova.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record EventExecutionResponse(Long executionId, String status) {}
