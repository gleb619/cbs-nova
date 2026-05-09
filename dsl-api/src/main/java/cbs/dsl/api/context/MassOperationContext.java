package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

// TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record MassOperationContext<T>(
    String performedBy, String dslVersion, Map<String, Object> enrichment, T payload) {}
