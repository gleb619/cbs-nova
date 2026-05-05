package cbs.dsl.api.context;

import java.util.Map;
import lombok.Builder;

@Builder
public record ConditionContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> params,
    Map<String, Object> enrichment,
    T payload) {

}