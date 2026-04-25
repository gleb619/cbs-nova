package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.List;

/**
 * Output carrier for workflow state transition execution. Carries the next state, event codes to
 * execute, and status as JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB
 * storage.
 */
@Json
public class WorkflowOutput {
  private final String nextState;
  private final List<String> events;
  private final String status;

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
