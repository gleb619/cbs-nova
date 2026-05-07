package cbs.dsl.api.context;

import java.util.Map;
import lombok.Builder;

//TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record HelperContext<T>(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> params,
    Map<String, Object> enrichment,
    T payload) {

}