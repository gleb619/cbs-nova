package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;
import java.util.function.BiFunction;

// TODO: remove
@Deprecated(forRemoval = true)
@Builder
public record FinishContext(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    Map<String, Object> enrichment,
    BiFunction<String, Map<String, Object>, Object> helperResolver) {}
