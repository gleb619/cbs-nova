package cbs.nova.starter;

import cbs.nova.dsl.DslException;
import io.sentry.Sentry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DslExceptionHandler {

  @ExceptionHandler(DslException.class)
  public ResponseEntity<ErrorResponse> handleDslException(DslException ex) {
    Sentry.configureScope(
            scope -> {
              scope.setTag("runId", ex.runId());
              scope.setTag("exceptionId", ex.exceptionId());
              scope.setTag("dslErrorCode", ex.code().name());
            });
    Sentry.captureException(ex);
    return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse(ex.code().name(), ex.getMessage(), null, ex.runId(),
                    ex.exceptionId()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
            .body(new ErrorResponse("BAD_REQUEST", ex.getMessage(), null, null, null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
    Sentry.captureException(ex);
    return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), null, null, null));
  }
}
