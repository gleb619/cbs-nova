package cbs.dsl.api;

import cbs.dsl.api.ContextTypes.ContextInput;
import cbs.dsl.api.ContextTypes.ContextOutput;
import cbs.dsl.api.ParametersTypes.ParameterError;
import cbs.dsl.api.ParametersTypes.ParametersInput;
import cbs.dsl.builder.TransactionDslObject;
import cbs.dsl.exception.ParametersValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface StandardDslDefinition extends DslDefinition {

  default List<ParameterDefinition> getParameters() {
    if (dsl() instanceof TransactionDslObject dsl) {
      return dsl.getParameters();
    }
    return Collections.emptyList();
  }

  /**
   * Validates input parameters against this object's parameter definitions.
   *
   * @param input the parameters to validate
   * @return list of validation errors, empty if valid
   */
  default List<ParameterError> validateParameters(ParametersInput input) {
    List<ParameterError> errors = new ArrayList<>();
    for (ParameterDefinition param : getParameters()) {
      ParameterError error = getValidator().validate(input, param);
      if (error != null) {
        errors.add(error);
      }
    }
    return errors;
  }

  default ContextOutput prepareContext(Map<String, Object> params) {
    if (dsl() instanceof StandardDslObject dsl) {
      if (Objects.nonNull(dsl.parameters())) {
        var fieldErrors = validateParameters(ParametersInput.from(params));
        if (!fieldErrors.isEmpty()) {
          throw new ParametersValidationException(fieldErrors);
        }
      }
      ContextOutput context;

      ContextInput input = ContextInput.from(params);
      if (Objects.nonNull(dsl.contextBlock())) {
        context = dsl.contextBlock().apply(input);
      } else {
        context = input.asOutput();
      }

      return context;
    }

    return ContextOutput.from(params);
  }

  default ParameterValidator getValidator() {
    return (input, param) -> null;
  }
}
