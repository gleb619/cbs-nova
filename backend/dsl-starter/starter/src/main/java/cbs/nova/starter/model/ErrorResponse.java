package cbs.nova.starter.model;

public record ErrorResponse(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId) {

}
