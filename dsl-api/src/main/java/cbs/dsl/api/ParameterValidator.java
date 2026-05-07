package cbs.dsl.api;

import cbs.dsl.api.ParametersTypes.ParameterError;
import cbs.dsl.api.ParametersTypes.ParametersInput;

/**
 * Validator for parameter definitions.
 */
@FunctionalInterface
public interface ParameterValidator {

  /**
   * Validates the input against this validator's rules.
   *
   * @param input the parameters input to validate
   * @return parameter error or null if valid
   */
  ParameterError validate(ParametersInput input, ParameterDefinition param);

}