package cbs.nova.starter.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

public record ErrorResponseContext(
        String code,
        String message,
        String entityName,
        String runId,
        String exceptionId,
        String suggestion,
        Map<String, Object> context) {

  @Builder
  @RequiredArgsConstructor
  private static final class ConstructorArgs {

    private final String code;
    private final String message;
    private final String entityName;
    private final String runId;
    private final String exceptionId;
    private final String suggestion;
    private final Map<String, Object> context;
  }

  public ErrorResponseContext {
    ConstructorArgs args = new ConstructorArgs(code, message, entityName, runId, exceptionId,
            suggestion, context);
    code = args.code;
    message = args.message;
    entityName = args.entityName;
    runId = args.runId;
    exceptionId = args.exceptionId;
    suggestion = args.suggestion;
    context = args.context;
  }

  public ErrorResponseContext(String code, String message, String entityName, String runId,
          String exceptionId) {
    this(code, message, entityName, runId, exceptionId, null, null);
  }

}
