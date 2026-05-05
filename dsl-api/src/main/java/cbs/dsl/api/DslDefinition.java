package cbs.dsl.api;

/**
 * Marker interface for code-generated DSL definitions.
 *
 * <p>Extends {@link DslObject} with a unified way to retrieve the canonical code. Implementations
 * of this interface are produced by the {@code dsl-codegen} module at compile time.
 */
public interface DslDefinition extends DslObject {}
