package cbs.nova.controller;

import cbs.nova.model.exception.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EventExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorDto> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(404).body(new ErrorDto(ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(422).body(new ErrorDto(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
    var fieldError = ex.getBindingResult().getFieldError();
    String message = fieldError != null
        ? "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage())
        : "Validation failed";
    return ResponseEntity.status(422).body(new ErrorDto(message));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorDto> handleInternal(RuntimeException ex) {
    return ResponseEntity.status(500).body(new ErrorDto("Internal execution error"));
  }

  record ErrorDto(String error) {

  }
}
