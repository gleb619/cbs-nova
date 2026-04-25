package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.Map;
import java.util.stream.Collectors;

/** TransactionInput carries the raw parameters and event metadata for executing a transaction. */
@Json
public class TransactionInput {
  private final Map<String, Object> params;
  private final String eventCode;
  private final Long eventNumber;
  private final String workflowExecutionId;

  public TransactionInput(Map<String, Object> params) {
    this(params, null, null, null);
  }

  public TransactionInput(Map<String, Object> params, String eventCode) {
    this(params, eventCode, null, null);
  }

  public TransactionInput(
      Map<String, Object> params, String eventCode, String workflowExecutionId) {
    this(params, eventCode, null, workflowExecutionId);
  }

  public TransactionInput(
      Map<String, Object> params, String eventCode, Long eventNumber, String workflowExecutionId) {
    this.params = params;
    this.eventCode = eventCode;
    this.eventNumber = eventNumber;
    this.workflowExecutionId = workflowExecutionId;
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

  /**
   * Returns parameters with nulls filtered out, suitable for TransactionContext.
   *
   * @return map with null values excluded
   */
  public Map<String, Object> nonNullParams() {
    return params.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
