package cbs.nova.starter;

public record ErrorResponse(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId) {
}
