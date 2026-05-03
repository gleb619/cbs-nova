package cbs.dsl.api;

import lombok.Builder;

@Builder
public record ParameterDefinition(String name, boolean required) {}
