package cbs.nova.starter.models;

public record ErrorResponse(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId) {
}
