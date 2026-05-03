package cbs.dsl.api;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Defines a helper — a reusable computation that can be invoked from event context blocks or
 * transactions.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code helper { }} block or annotated
 * with {@link DslComponent} for compile-time registration.
 */
public interface HelperDefinition extends DslDefinition {

  /**
   * Canonical code used to look up this helper in the registry.
   *
   * @return the helper code
   */
  String getCode();

  /**
   * Optional display name for this helper. Used to distinguish DSL overrides from the underlying
   * implementation class/bean identified by {@link #getCode()}.
   *
   * @return the display name, or {@code null}
   */
  default String getName() {
    return null;
  }

  /**
   * List of parameter definitions declared in the {@code parameters { }} block. Used for validation
   * and documentation purposes.
   *
   * @return the parameter definitions
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

  /**
   * Optional context enrichment block that runs before the execute block. Allows helpers to enrich
   * the context with additional data before execution.
   *
   * @return the context block
   */
  default Consumer<HelperContext> getContextBlock() {
    return ctx -> {};
  }

  /**
   * Executes this helper with the given typed input.
   *
   * @param input the helper input
   * @return the helper output
   */
  HelperOutput execute(HelperInput input);
}
