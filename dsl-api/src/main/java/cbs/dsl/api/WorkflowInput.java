package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.Map;

/**
 * Input carrier for workflow state transition execution. Carries the current state, action to
 * perform, parameters, and optional workflow instance metadata as JSON-serializable data for
 * Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
public class WorkflowInput {
  private final String currentState;
  private final String action;
  private final Map<String, Object> params;
  private final String workflowInstanceId;

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
