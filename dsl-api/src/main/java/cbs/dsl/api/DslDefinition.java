package cbs.dsl.api;

import cbs.dsl.builder.UndefinedDslObject;

/**
 * Marker interface for code-generated DSL definitions.
 *
 * <p>Extends {@link DslObject} with a unified way to retrieve the canonical code. Implementations
 * of this interface are produced by the {@code dsl-codegen} module at compile time.
 */
public interface DslDefinition {

  /**
   * Canonical code used to look up this transaction in the registry.
   *
   * @return the transaction code
   */
  default String getCode() {
    return this.getClass().getSimpleName();
  }

  default String getVersion() {
    return "0000000";
  }

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default DslObject dsl() {
    return UndefinedDslObject.create();
  }
}
