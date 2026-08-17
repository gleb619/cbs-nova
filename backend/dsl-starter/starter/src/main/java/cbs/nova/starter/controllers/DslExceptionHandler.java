package cbs.nova.starter.controllers;

import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.error.DslExceptionMapper;
import cbs.nova.starter.models.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Translates exceptions thrown by cbs-nova controllers into {@link ErrorResponse} bodies. All
 * mapping logic is delegated to the injected {@link DslExceptionMapper} so host applications can
 * supply a custom implementation.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class DslExceptionHandler {

  private final DslExceptionMapper dslExceptionMapper;

  @ExceptionHandler(DslException.class)
  public ResponseEntity<ErrorResponse> handleDslException(DslException ex, WebRequest request) {
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex,
          WebRequest request) {
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
    return dslExceptionMapper.handle(ex, request);
  }
}
