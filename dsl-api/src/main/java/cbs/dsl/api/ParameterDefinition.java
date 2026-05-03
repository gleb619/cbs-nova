package cbs.dsl.api;

import lombok.Builder;

/**
 * Defines a parameter accepted by a DSL component (event, transaction, helper, condition, etc.).
 */
@Builder
public record ParameterDefinition(String name, boolean required) {}
