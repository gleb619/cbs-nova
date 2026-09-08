package cbs.nova.dsl.builder.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class CompileException extends RuntimeException {

  private final List<String> diagnostics;

  public CompileException(String message, List<String> diagnostics) {
    super(message);
    this.diagnostics = List.copyOf(diagnostics);
  }

  public CompileException(String message, Throwable cause) {
    super(message, cause);
    this.diagnostics = List.of(message);
  }
}
