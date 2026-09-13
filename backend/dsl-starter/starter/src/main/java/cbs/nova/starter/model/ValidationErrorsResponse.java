package cbs.nova.starter.model;

import java.util.List;


public record ValidationErrorsResponse(List<ValidationError> errors) {
}
