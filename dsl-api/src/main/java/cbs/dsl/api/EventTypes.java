package cbs.dsl.api;

import cbs.dsl.api.EventFunction.EventArg;
import cbs.dsl.api.EventFunction.EventResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated event DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTypes {

  @Json
  @Builder(toBuilder = true)
  public record EventInput(
      Map<String, Object> params, String eventCode, Long eventNumber, String workflowExecutionId)
      implements EventArg {

    public EventInput(Map<String, Object> params) {
      this(params, null, null, null);
    }

    public EventInput(Map<String, Object> params, String eventCode) {
      this(params, eventCode, null, null);
    }

    @Override
    public Map<String, Object> toMap() {
      return params;
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

  @Json
  @Builder(toBuilder = true)
  public record EventOutput(Map<String, Object> result, String status) implements EventResult {

    public EventOutput(Map<String, Object> result) {
      this(result, "SUCCESS");
    }

    @Override
    public Map<String, Object> toMap() {
      return result;
    }

    public Map<String, Object> result() {
      return result;
    }

    public String status() {
      return status;
    }

    // JavaBean-style getters for Kotlin property access
    public Map<String, Object> getResult() {
      return result;
    }

    public String getStatus() {
      return status;
    }
  }
}
