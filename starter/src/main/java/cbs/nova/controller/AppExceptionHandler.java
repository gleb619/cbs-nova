package cbs.nova.controller;

import cbs.nova.model.ErrorResponse;
import cbs.nova.model.exception.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class AppExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      EntityNotFoundException ex, HttpServletRequest request) {
    log.error("Entity for given identity is not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.error("Got wrong argument: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(buildErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.error("Data validation error: {}", ex.getMessage());
    var fieldError = ex.getBindingResult().getFieldError();
    String message = fieldError != null
        ? "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage())
        : "Validation failed";
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, message, request.getRequestURI()));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponse> handleInternal(
      RuntimeException ex, HttpServletRequest request) {
    log.error("Internal error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", request.getRequestURI()));
  }

  private ErrorResponse buildErrorResponse(HttpStatus status, String message, String path) {
    return new ErrorResponse(
        Instant.now(), status.value(), status.getReasonPhrase(), message, path);
  }
}
