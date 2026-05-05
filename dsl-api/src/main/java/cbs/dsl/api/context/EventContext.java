package cbs.dsl.api.context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.Builder;

//TODO: replace with real impl context in correspondent classes
@Builder
public record EventContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    Map<String, Object> enrichment,
    BiFunction<String, Map<String, Object>, Object> helperResolver,
    T payload) {

}