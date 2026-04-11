package cbs.nova.controller;

import cbs.nova.model.exception.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SettingExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Void> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.notFound().build();
  }
}
