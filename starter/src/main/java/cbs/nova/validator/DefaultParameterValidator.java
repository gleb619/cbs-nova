package cbs.nova.validator;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.ParameterValidator;
import cbs.dsl.api.ParametersTypes.ParameterError;
import cbs.dsl.api.ParametersTypes.ParametersInput;

/**
 * Default implementation of ParameterValidator.
 */
public class DefaultParameterValidator implements ParameterValidator {

  @Override
  public ParameterError validate(ParametersInput input, ParameterDefinition param) {
    boolean present = input.isPresent(param.getName());

    switch (param.getType()) {
      case STRING -> {
        if (present && !input.isString(param.getName())) {
          return ParameterError.nonString(param.getName());
        }
      }
      case INTEGER -> {
        if (present && !input.isNumber(param.getName())) {
          return ParameterError.nonNumber(param.getName());
        }
      }
      case DECIMAL -> {
        if (present && !input.isDecimal(param.getName())) {
          return ParameterError.nonDecimal(param.getName());
        }
      }
      case BOOLEAN -> {
        if (present && !input.isBoolean(param.getName())) {
          return ParameterError.nonBoolean(param.getName());
        }
      }
      //TODO: replace with project related exception
      default -> throw new IllegalStateException("Unexpected value: " + param.getType());
    }
    return null;
  }
}