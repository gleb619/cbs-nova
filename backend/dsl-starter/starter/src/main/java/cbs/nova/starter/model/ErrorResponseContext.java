package cbs.nova.starter.model;

/**
 * Carries the five fields needed to build an {@link ErrorResponse} without coupling the mapper
 * to the raw {@link Throwable} / {@link cbs.nova.dsl.PreviewReport} inputs that produce them.
 */
public record ErrorResponseContext(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId) {

}