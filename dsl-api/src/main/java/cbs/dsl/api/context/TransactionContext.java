package cbs.dsl.api.context;

import java.util.Map;
import java.util.function.BiFunction;
import lombok.Builder;

//TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record TransactionContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    Map<String, Object> enrichment,
    BiFunction<String, Map<String, Object>, Object> helperResolver,
    boolean isResumed,
    T payload) {

}
