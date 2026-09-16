package cbs.nova.starter.model;

import java.util.Map;

public record ErrorResponseContext(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId,
        String suggestion,
        Map<String, Object> context) {

  public ErrorResponseContext(String code, String message, String entityName, String runId,
          String exceptionId) {
    this(code, message, entityName, runId, exceptionId, null, null);
  }

}
