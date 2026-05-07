package cbs.dsl.api;

import cbs.dsl.api.WorkflowFunction.WorkflowArg;
import cbs.dsl.api.WorkflowFunction.WorkflowResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowTypes {

  @Json
  @Builder(toBuilder = true)
  public record WorkflowInput(
      String currentState, String action, Map<String, Object> params, String workflowInstanceId)
      implements WorkflowArg {

  }

  @Json
  @Builder(toBuilder = true)
  public record WorkflowOutput(String nextState, List<String> events, String status)
      implements WorkflowResult {

    public WorkflowOutput(String nextState) {
      this(nextState, List.of(), "SUCCESS");
    }

  }
}
