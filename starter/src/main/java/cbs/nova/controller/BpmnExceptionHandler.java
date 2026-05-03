package cbs.nova.controller;

import cbs.nova.bpmn.WorkflowNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BpmnExceptionHandler {

  @ExceptionHandler(WorkflowNotFoundException.class)
  public ResponseEntity<Void> handleWorkflowNotFound(WorkflowNotFoundException ex) {
    return ResponseEntity.notFound().build();
  }
}
