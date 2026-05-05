package cbs.dsl.api.context;

import java.util.Map;
import lombok.Builder;

@Builder
public record ParameterContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    T payload) {

}