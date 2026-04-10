package cbs.nova.controller;

import cbs.nova.model.exception.SettingNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SettingExceptionHandler {

  @ExceptionHandler(SettingNotFoundException.class)
  public ResponseEntity<Void> handleNotFound(SettingNotFoundException ex) {
    return ResponseEntity.notFound().build();
  }
}
