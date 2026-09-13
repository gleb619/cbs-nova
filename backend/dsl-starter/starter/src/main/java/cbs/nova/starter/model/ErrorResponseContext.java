package cbs.nova.starter.model;

public record ErrorResponseContext(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId) {

}
