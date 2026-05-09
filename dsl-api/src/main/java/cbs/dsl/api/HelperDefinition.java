package cbs.dsl.api;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;

/**
 * Defines a helper — a reusable computation that can be invoked from event context blocks or
 * transactions.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code helper { }} block or annotated
 * with {@link DslComponent} for compile-time registration.
 */
public interface HelperDefinition extends StandardDslDefinition {

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
}
