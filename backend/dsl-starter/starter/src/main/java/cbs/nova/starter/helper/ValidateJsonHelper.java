package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.ValidateJsonIn;
import cbs.nova.starter.helper.model.ValidateJsonOut;
import cbs.nova.starter.validation.JsonSchemaValidator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates a JSON payload against a JSON Schema.
 *
 * <p>
 * The helper accepts a {@code payload} (the JSON string to validate) and a {@code schema} (a JSON
 * object string describing the expected structure). It returns a list of {@code ValidationError}
 * items and a convenience {@code valid} flag that is {@code true} when the error list is empty.
 * Validation is pure: the payload is not modified and no external call is made.
 */
@Helper(name = "validateJson")
public class ValidateJsonHelper implements Executable<ValidateJsonIn, ValidateJsonOut> {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public @NonNull Result<ValidateJsonOut> execute(@NonNull Context<ValidateJsonIn> ctx) {
    ValidateJsonIn input = ctx.body();
    if (input.payload() == null || input.payload().isBlank()) {
      return Result.failure(new IllegalArgumentException("validateJson.payload is required"));
    }
    if (input.schema() == null || input.schema().isBlank()) {
      return Result.failure(new IllegalArgumentException("validateJson.schema is required"));
    }

    try {
      Object body = mapper.readValue(input.payload(), Object.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> schema = mapper.readValue(input.schema(), Map.class);
      List<cbs.nova.starter.model.ValidationError> errors = JsonSchemaValidator
              .validate(body, schema);
      return Result.success(new ValidateJsonOut(errors, errors.isEmpty()));
    } catch (RuntimeException e) {
      return Result.failure(new IllegalArgumentException(
              "validateJson: invalid payload or schema: " + e.getMessage(), e));
    }
  }
}
