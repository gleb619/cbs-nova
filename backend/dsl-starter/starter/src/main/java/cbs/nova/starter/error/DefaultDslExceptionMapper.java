package cbs.nova.starter.error;

import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.model.ErrorResponse;
import io.sentry.Sentry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

public class DefaultDslExceptionMapper implements DslExceptionMapper {

  @Override
  public ResponseEntity<ErrorResponse> handle(Exception exception, WebRequest request) {
    if (exception instanceof DslException dsl) {
      capture(dsl, dsl.runId());
      return ResponseEntity.unprocessableEntity()
              .body(new ErrorResponse(dsl.code().name(), dsl.getMessage(), null, dsl.runId(),
                      dsl.exceptionId()));
    }
    if (exception instanceof IllegalArgumentException illegalArgument) {
      return ResponseEntity.badRequest()
              .body(new ErrorResponse("BAD_REQUEST", illegalArgument.getMessage(), null, null,
                      null));
    }
    String runId = runIdFrom(request);
    capture(exception, runId);
    return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", exception.getMessage(), null, null, null));
  }

  private static String runIdFrom(WebRequest request) {
    Object runId = request.getAttribute("runId", WebRequest.SCOPE_REQUEST);
    return runId instanceof String s && !s.isBlank() ? s : null;
  }

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
