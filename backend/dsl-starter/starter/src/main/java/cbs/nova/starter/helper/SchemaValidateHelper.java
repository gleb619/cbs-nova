package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.SchemaValidateIn;
import cbs.nova.starter.helper.model.SchemaValidateOut;
import cbs.nova.starter.model.ValidationError;
import cbs.nova.starter.validation.JsonSchemaValidator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates a JSON payload against a JSON Schema, returning structured errors and a summary.
 *
 * <p>
 * The helper accepts a {@code payload} (the JSON string to validate), a {@code schema} (a JSON
 * object string describing the expected structure), and an optional {@code failFast} flag (default
 * {@code false}). It always reuses the same {@link cbs.nova.starter.validation.JsonSchemaValidator}
 * that {@code InputValidator} runs server-side, so the DSL-side behaviour matches the runtime
 * validation contract exactly.
 *
 * <p>
 * Validation is pure: the payload is not modified and no external call is made. The helper reports
 * either zero errors ({@code valid = true}, summary {@code "valid"}) or one or more errors each
 * carrying a JSONPath-style {@code path} and a human-readable {@code message}. When
 * {@code failFast} is {@code true}, validation stops at the first reported error.
 */
@Helper(name = "schemaValidate")
public class SchemaValidateHelper implements Executable<SchemaValidateIn, SchemaValidateOut> {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public @NonNull Result<SchemaValidateOut> execute(@NonNull Context<SchemaValidateIn> ctx) {
    SchemaValidateIn input = ctx.body();
    if (input == null) {
      return Result.failure(new IllegalArgumentException("schemaValidate input is required"));
    }
    if (input.payload() == null || input.payload().isBlank()) {
      return Result.failure(new IllegalArgumentException("schemaValidate.payload is required"));
    }
    if (input.schema() == null || input.schema().isBlank()) {
      return Result.failure(new IllegalArgumentException("schemaValidate.schema is required"));
    }

    Object body;
    Map<String, Object> schema;
    try {
      body = mapper.readValue(input.payload(), Object.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = mapper.readValue(input.schema(), Map.class);
      schema = parsed;
    } catch (JacksonException e) {
      return Result.failure(new IllegalArgumentException(
              "schemaValidate: invalid payload or schema: " + e.getOriginalMessage(), e));
    } catch (RuntimeException e) {
      return Result.failure(new IllegalArgumentException(
              "schemaValidate: invalid payload or schema: " + e.getMessage(), e));
    }

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);
    boolean failFast = Boolean.TRUE.equals(input.failFast());
    if (failFast && errors.size() > 1) {
      errors = List.of(errors.get(0));
    }

    boolean valid = errors.isEmpty();
    String summary = valid
            ? "valid"
            : "invalid: " + errors.size() + " error(s)";
    return Result.success(new SchemaValidateOut(errors, valid, summary));
  }
}
