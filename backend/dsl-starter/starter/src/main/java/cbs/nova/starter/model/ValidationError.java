package cbs.nova.starter.model;


public record ValidationError(String field, String message, String severity) {
}
