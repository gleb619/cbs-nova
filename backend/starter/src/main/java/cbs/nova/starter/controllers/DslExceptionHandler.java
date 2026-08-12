package cbs.nova.starter.controllers;

import cbs.nova.dsl.DslException;
import cbs.nova.starter.models.ErrorResponse;
import io.sentry.Sentry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class DslExceptionHandler {

  @ExceptionHandler(DslException.class)
  public ResponseEntity<ErrorResponse> handleDslException(DslException ex) {
    capture(ex, ex.runId());
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
  public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
    String runId = runIdFrom(request);
    capture(ex, runId);
    return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), null, null, null));
  }

  private static String runIdFrom(WebRequest request) {
    Object runId = request.getAttribute("runId", WebRequest.SCOPE_REQUEST);
    return runId instanceof String s && !s.isBlank() ? s : null;
  }

  // TODO: Instead of static, create interface and some impl, to end-user can override it
  private static void capture(Exception ex, String runId) {
    try {
      if (runId != null) {
        Sentry.setTag("runId", runId);
      }
      Sentry.captureException(ex);
    } catch (Exception ignored) {
      // Sentry is optional; unconfigured SDK calls are no-ops, but guard defensively.
    }
  }
}
