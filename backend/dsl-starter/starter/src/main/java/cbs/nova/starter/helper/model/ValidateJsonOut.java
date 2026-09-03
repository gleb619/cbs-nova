package cbs.nova.starter.helper.model;

import cbs.nova.starter.model.ValidationError;
import java.util.List;

/**
 * Output of the built-in {@code validateJson} helper.
 *
 * <p>
 * {@code errors} is the list of validation problems reported by
 * {@link cbs.nova.starter.validation.JsonSchemaValidator}. {@code valid} is {@code true} exactly
 * when the error list is empty.
 */
public record ValidateJsonOut(List<ValidationError> errors, boolean valid) {
}
