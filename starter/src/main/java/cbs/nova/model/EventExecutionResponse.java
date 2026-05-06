package cbs.nova.model;

import lombok.Builder;

//TODO: remove file, instead use another api
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record EventExecutionResponse(Long executionId, String status) {}
