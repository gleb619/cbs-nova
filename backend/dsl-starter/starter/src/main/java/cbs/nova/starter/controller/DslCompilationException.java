package cbs.nova.starter.controller;

import cbs.nova.starter.model.CompileDiagnostic;

import java.util.List;

public class DslCompilationException extends RuntimeException {

  private final List<CompileDiagnostic> diagnostics;

  public DslCompilationException(String message, List<CompileDiagnostic> diagnostics) {
    super(message);
    this.diagnostics = diagnostics;
  }

  public List<CompileDiagnostic> diagnostics() {
    return diagnostics;
  }

}
