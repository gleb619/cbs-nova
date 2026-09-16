package cbs.nova.dsl.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class ErrorResponse {

  private final String code;
  private final String message;
  private final String entityName;
  private final String runId;
  private final String correlationId;
  private final String exceptionId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<CompileDiagnostic> diagnostics;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String suggestion;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final Map<String, Object> context;

  @JsonCreator
  public ErrorResponse(
          @JsonProperty("code") String code,
          @JsonProperty("message") String message,
          @JsonProperty("entityName") String entityName,
          @JsonProperty("runId") String runId,
          @JsonProperty("correlationId") String correlationId,
          @JsonProperty("exceptionId") String exceptionId,
          @JsonProperty("diagnostics") List<CompileDiagnostic> diagnostics,
          @JsonProperty("suggestion") String suggestion,
          @JsonProperty("context") Map<String, Object> context) {
    this.code = code;
    this.message = message;
    this.entityName = entityName;
    this.runId = runId;
    this.correlationId = correlationId;
    this.exceptionId = exceptionId;
    this.diagnostics = diagnostics;
    this.suggestion = suggestion;
    this.context = context;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }

  public String entityName() {
    return entityName;
  }

  public String runId() {
    return runId;
  }

  public String correlationId() {
    return correlationId;
  }

  public String exceptionId() {
    return exceptionId;
  }

  public List<CompileDiagnostic> diagnostics() {
    return diagnostics;
  }

  public String suggestion() {
    return suggestion;
  }

  public Map<String, Object> context() {
    return context;
  }

}
