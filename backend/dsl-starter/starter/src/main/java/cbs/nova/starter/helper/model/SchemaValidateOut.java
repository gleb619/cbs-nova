package cbs.nova.starter.helper.model;

import cbs.nova.starter.model.ValidationError;
import java.util.List;

/**
 * Output of the built-in {@code schemaValidate} helper.
 *
 * <p>
 * {@code errors} is the list of validation problems reported by
 * {@link cbs.nova.starter.validation.JsonSchemaValidator}; each item carries a {@code path}
 * (JSONPath-style, {@code $.field[0].nested}) and a human-readable {@code message}. {@code valid}
 * is {@code true} exactly when the error list is empty. {@code summary} is a short single-line
 * description suitable for logs / surfaces, e.g. {@code "valid"} or {@code "invalid: 3 error(s)"}.
 */
public record SchemaValidateOut(List<ValidationError> errors, boolean valid, String summary) {
}
