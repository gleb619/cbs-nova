package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

// TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record ConditionContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> params,
    Map<String, Object> enrichment,
    T payload) {}
