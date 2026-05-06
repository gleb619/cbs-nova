package cbs.dsl.api;

/**
 * Marker interface for code-generated DSL definitions.
 *
 * <p>Extends {@link DslObject} with a unified way to retrieve the canonical code. Implementations
 * of this interface are produced by the {@code dsl-codegen} module at compile time.
 */
public interface DslDefinition<T extends DslObject> {

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default T dsl() {
    throw new NullPointerException("Dsl object not added");
  }

}
