package cbs.dsl.api;

import io.avaje.jsonb.Json;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTypes {

  @Json
  @Builder(toBuilder = true)
  public record EventInput(
    Map<String, Object> params,
    String eventCode,
    Long eventNumber,
    String workflowExecutionId
  ) {

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

  @Json
  @Builder(toBuilder = true)
  public record EventOutput(
    Map<String, Object> context,
    Map<String, Map<String, Object>> transactionResults,
    String status
  ) {

    public EventOutput(
        Map<String, Object> context, Map<String, Map<String, Object>> transactionResults) {
      this(context, transactionResults, "SUCCESS");
    }

    public Map<String, Object> context() {
      return context;
    }

    public Map<String, Map<String, Object>> transactionResults() {
      return transactionResults;
    }

    public String status() {
      return status;
    }

    // JavaBean-style getters for Kotlin property access
    public Map<String, Object> getContext() {
      return context;
    }

    public Map<String, Map<String, Object>> getTransactionResults() {
      return transactionResults;
    }

    public String getStatus() {
      return status;
    }
  }
  
}
