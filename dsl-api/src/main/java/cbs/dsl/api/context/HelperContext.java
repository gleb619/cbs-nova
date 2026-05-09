package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record HelperContext<T>(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    T payload) {}
