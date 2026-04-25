package cbs.dsl.api;

import io.avaje.jsonb.Json;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowTypes {

  @Json
  @Builder(toBuilder = true)
  public record WorkflowInput(
    String currentState,
    String action,
    Map<String, Object> params,
    String workflowInstanceId
  ) {

    public WorkflowInput(String currentState, String action) {
      this(currentState, action, Map.of(), null);
    }

    public WorkflowInput(
        String currentState, String action, Map<String, Object> params, String workflowInstanceId) {
      this.currentState = currentState;
      this.action = action;
      this.params = params;
      this.workflowInstanceId = workflowInstanceId;
    }

    public String currentState() {
      return currentState;
    }

    public String action() {
      return action;
    }

    public Map<String, Object> params() {
      return params;
    }

    public String workflowInstanceId() {
      return workflowInstanceId;
    }

    // JavaBean-style getters for Kotlin property access
    public String getCurrentState() {
      return currentState;
    }

    public String getAction() {
      return action;
    }

    public Map<String, Object> getParams() {
      return params;
    }

    public String getWorkflowInstanceId() {
      return workflowInstanceId;
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record WorkflowOutput(
    String nextState,
    List<String> events,
    String status
  ) {

    public WorkflowOutput(String nextState) {
      this(nextState, List.of(), "SUCCESS");
    }

    public WorkflowOutput(String nextState, List<String> events, String status) {
      this.nextState = nextState;
      this.events = events;
      this.status = status;
    }

    public String nextState() {
      return nextState;
    }

    public List<String> events() {
      return events;
    }

    public String status() {
      return status;
    }

    // JavaBean-style getters for Kotlin property access
    public String getNextState() {
      return nextState;
    }

    public List<String> getEvents() {
      return events;
    }

    public String getStatus() {
      return status;
    }
  }

}
