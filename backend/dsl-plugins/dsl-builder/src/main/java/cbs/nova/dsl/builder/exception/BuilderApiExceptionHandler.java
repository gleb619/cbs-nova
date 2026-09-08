package cbs.nova.dsl.builder.exception;

import cbs.nova.dsl.builder.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BuilderApiExceptionHandler {

  @ExceptionHandler(BuilderApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(BuilderApiException ex) {
    return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(ex.getCode(), ex.getMessage(), null, null, null));
  }

  @ExceptionHandler(BuilderBusyException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ErrorResponse handleBusy(BuilderBusyException ex) {
    return new ErrorResponse("BUILDER_BUSY", messageOf(ex), null, null, null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleBadRequest(IllegalArgumentException ex) {
    return new ErrorResponse("INVALID_REQUEST", messageOf(ex), null, null, null);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleUnreadableBody(
          HttpMessageNotReadableException ex) {
    return new ErrorResponse("INVALID_REQUEST", "malformed request body", null, null, null);
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ErrorResponse handleBulkhead(IllegalStateException ex) {
    return new ErrorResponse("BULKHEAD_SATURATED", messageOf(ex), null, null, null);
  }

  private String messageOf(RuntimeException ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }
}
