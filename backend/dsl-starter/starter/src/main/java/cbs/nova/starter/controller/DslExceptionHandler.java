package cbs.nova.starter.controller;

import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.converter.DslExceptionMapper;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.model.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class DslExceptionHandler extends ResponseEntityExceptionHandler {

  private final DslExceptionMapper dslExceptionMapper;

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex,
          @Nullable Object body,
          HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    log.error("ERROR_INTERNAL: {}", ex.getMessage(), ex);
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  @ExceptionHandler(DslPayloadTooLargeException.class)
  public ResponseEntity<ErrorResponse> handlePayloadTooLarge(DslPayloadTooLargeException ex,
          WebRequest request) {
    log.error("PAYLOAD_TOO_LARGE: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse("PAYLOAD_TOO_LARGE", ex.getMessage(), ex.getEntityName(), null,
                    null));
  }

  @ExceptionHandler(DslException.class)
  public ResponseEntity<ErrorResponse> handleDslException(DslException ex, WebRequest request) {
    log.error("DSL_ERROR: {}", ex.getMessage(), ex);
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex,
          WebRequest request) {
    log.error("IA_ERROR: {}", ex.getMessage(), ex);
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
    log.error("ERROR: {}", ex.getMessage(), ex);
    return dslExceptionMapper.handle(ex, request);
  }
}
