package cbs.dsl.api;

/**
 * Marker interface for all DSL definitions.
 *
 * <p>Provides a unified way to retrieve the canonical code of any DSL definition (event,
 * transaction, workflow, condition, mass operation, or helper).
 */
public interface DslDefinition {

  /**
   * Canonical code used to identify this definition in registries and workflow transitions.
   *
   * @return the definition code
   */
  String getCode();
}
