package cbs.nova.controller;

import cbs.nova.model.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AppExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorDto> handleNotFound(EntityNotFoundException ex) {
    log.error("Entity for given identity is not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException ex) {
    log.error("Got wrong argument: {}", ex.getMessage());
    return ResponseEntity.status(422).body(new ErrorDto(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
    log.error("Data validation error: {}", ex.getMessage());
    var fieldError = ex.getBindingResult().getFieldError();
    String message = fieldError != null
        ? "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage())
        : "Validation failed";
    return ResponseEntity.status(422).body(new ErrorDto(message));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorDto> handleInternal(RuntimeException ex) {
    log.error("Internal error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorDto("Internal error"));
  }

  // TODO: redo to normal error response
  record ErrorDto(String error) {}
}
