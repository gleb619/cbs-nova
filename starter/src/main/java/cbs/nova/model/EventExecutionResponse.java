package cbs.nova.model;

import cbs.dsl.api.EventTypes.EventStatus;
import lombok.Builder;

@Builder(toBuilder = true)
public record EventExecutionResponse(String executionId, EventStatus status) {}
