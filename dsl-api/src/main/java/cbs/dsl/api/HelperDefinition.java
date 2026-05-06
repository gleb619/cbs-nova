package cbs.dsl.api;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;

import cbs.dsl.builder.HelperDslObject;
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
public interface HelperDefinition extends DslDefinition<HelperDslObject> {

  /**
   * Canonical code used to look up this helper in the registry.
   *
   * @return the helper code
   */
  String getCode();

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
   * Executes this helper with the given typed input.
   *
   * @param input the helper input
   * @return the helper output
   */
  HelperOutput execute(HelperInput input);

  default HelperOutput preview(HelperInput input) {
    return execute(input);
  }

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default HelperDslObject dsl() {
    throw new NullPointerException("Dsl object not added");
  }
}
