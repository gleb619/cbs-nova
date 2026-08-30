package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CompileDiagnostic> diagnostics) {

  public ErrorResponse(String code, String message, String entityName, String runId,
          String exceptionId) {
    this(code, message, entityName, runId, exceptionId, null);
  }

}
