package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input carrier for event execution. Carries event parameters and execution metadata as
 * JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EventInput {
  private Map<String, Object> params;
  private String eventCode;
  private Long eventNumber;
  private String workflowExecutionId;

  public EventInput(Map<String, Object> params, String eventCode) {
    this(params, eventCode, null, null);
  }

  public Map<String, Object> params() {
    return params;
  }

  public String eventCode() {
    return eventCode;
  }

  public Long eventNumber() {
    return eventNumber;
  }

  public String workflowExecutionId() {
    return workflowExecutionId;
  }

  // JavaBean-style getters for Kotlin property access
  public Map<String, Object> getParams() {
    return params;
  }

  public String getEventCode() {
    return eventCode;
  }

  public Long getEventNumber() {
    return eventNumber;
  }

  public String getWorkflowExecutionId() {
    return workflowExecutionId;
  }
}
