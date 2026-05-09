package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;
import java.util.function.BiFunction;

// TODO: replace with real impl context in correspondent classes
// TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record EventContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    Map<String, Object> enrichment,
    BiFunction<String, Map<String, Object>, Object> helperResolver,
    T payload) {}
