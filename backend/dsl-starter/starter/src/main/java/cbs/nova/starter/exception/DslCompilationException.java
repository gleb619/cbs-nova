package cbs.nova.starter.exception;

import cbs.nova.dsl.model.CompileDiagnostic;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DslCompilationException extends RuntimeException {

  private final String message;
  private final List<CompileDiagnostic> diagnostics;

  public List<CompileDiagnostic> diagnostics() {
    return diagnostics;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
