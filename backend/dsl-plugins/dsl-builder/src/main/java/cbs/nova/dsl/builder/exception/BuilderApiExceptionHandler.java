package cbs.nova.dsl.builder.exception;

import cbs.nova.dsl.BuilderErrorResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BuilderApiExceptionHandler {

  @ExceptionHandler(BuilderApiException.class)
  public ResponseEntity<BuilderErrorResponse> handleApi(BuilderApiException ex) {
    return ResponseEntity.status(ex.getStatus())
            .body(new BuilderErrorResponse(ex.getCode(), ex.getMessage(), null, null));
  }

  @ExceptionHandler(BuilderBusyException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public BuilderErrorResponse handleBusy(BuilderBusyException ex) {
    return of("BUILDER_BUSY", messageOf(ex));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public BuilderErrorResponse handleBadRequest(IllegalArgumentException ex) {
    return of("INVALID_REQUEST", messageOf(ex));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public BuilderErrorResponse handleUnreadableBody(
          HttpMessageNotReadableException ex) {
    return of("INVALID_REQUEST", "malformed request body");
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public BuilderErrorResponse handleBulkhead(IllegalStateException ex) {
    return of("BULKHEAD_SATURATED", messageOf(ex));
  }

  private static BuilderErrorResponse of(String code, String message) {
    return new BuilderErrorResponse(code, message, null, List.of());
  }

  private String messageOf(RuntimeException ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }
}
