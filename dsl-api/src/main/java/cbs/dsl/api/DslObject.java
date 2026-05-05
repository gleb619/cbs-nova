package cbs.dsl.api;

/**
 * Marker interface for all DSL objects — both builder outputs and code-generated definitions.
 *
 * <p>Provides a unified way to retrieve the canonical code of any DSL object.
 */
public interface DslObject {

  /**
   * Canonical code used to identify this object in registries and workflow transitions.
   *
   * @return the object code
   */
  String getCode();
}
