package cbs.nova.starter.converter;

import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ErrorResponseContext;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

/**
 * Centralises {@link ErrorResponse} construction for the runtime endpoints. Each
 * {@code fromXxx(...)} default method captures one input shape (DSL exception, generic throwable,
 * preview report failure) so the service can pick the right one without the UUID / message / code
 * resolution logic leaking into business code.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DslRuntimeMapper {

  ErrorResponse toErrorResponse(ErrorResponseContext context);

  default ErrorResponseContext fromDslException(DslException cause, String entityName) {
    return new ErrorResponseContext(
            cause.code().name(),
            cause.getMessage(),
            entityName,
            cause.runId(),
            cause.exceptionId());
  }

  default ErrorResponseContext fromThrowable(String entityName, String runId, Throwable cause) {
    String exceptionId = runId + ":ex:" + UUID.randomUUID();
    String message = cause.getMessage() != null
            ? cause.getMessage()
            : cause.getClass().getSimpleName();
    return new ErrorResponseContext("EXECUTION_FAILED", message, entityName, runId, exceptionId);
  }

  default ErrorResponseContext fromPreviewReport(String entityName, String runId,
          PreviewReport report) {
    PreviewErrorDetail firstError = report != null && !report.errors().isEmpty()
            ? report.errors().get(0)
            : null;
    String code = firstError != null ? firstError.code().name() : "EXECUTION_FAILED";
    String message = firstError != null && firstError.message() != null
            ? firstError.message()
            : "Preview failed";
    String exceptionId = runId + ":ex:" + UUID.randomUUID();
    return new ErrorResponseContext(code, message, entityName, runId, exceptionId);
  }
}
