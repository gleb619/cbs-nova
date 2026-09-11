package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@RequiredArgsConstructor
@Jacksonized
@Getter
@EqualsAndHashCode
@ToString
public class ErrorResponse {

  private final String code;
  private final String message;
  private final String entityName;
  private final String runId;
  private final String exceptionId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<CompileDiagnostic> diagnostics;

}
