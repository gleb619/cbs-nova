package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

// TODO: remove
@Deprecated(forRemoval = true)
@Builder
public record ParameterContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    T payload) {}
