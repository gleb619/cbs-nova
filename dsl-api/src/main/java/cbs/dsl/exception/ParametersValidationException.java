package cbs.dsl.exception;

import cbs.dsl.api.ParametersTypes.ParameterError;
import java.util.List;

public class ParametersValidationException extends DslException {

  private final List<ParameterError> errors;

  public ParametersValidationException(List<ParameterError> errors) {
    super("Parameters validation failed: %s".formatted(errors));
    this.errors = List.copyOf(errors);
  }

  public List<ParameterError> errors() {
    return errors;
  }
}
