package cbs.nova.starter.model;

import java.util.List;

/**
 * Body returned for HTTP 422 responses caused by input schema validation failures.
 */
public record ValidationErrorsResponse(List<ValidationError> errors) {
}
