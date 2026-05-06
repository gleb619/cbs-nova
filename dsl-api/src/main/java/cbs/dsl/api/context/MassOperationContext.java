package cbs.dsl.api.context;

import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record MassOperationContext<T>(
    String performedBy,
    String dslVersion,
    Map<String, Object> enrichment,
    T payload) {

}