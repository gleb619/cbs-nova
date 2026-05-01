package cbs.dsl.api;

import cbs.dsl.api.TransactionFunction.TransactionArg;
import cbs.dsl.api.TransactionFunction.TransactionResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionTypes {

  @Json
  @Builder(toBuilder = true)
  public record TransactionInput(
      Map<String, Object> params, String eventCode, Long eventNumber, String workflowExecutionId)
      implements TransactionArg {

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
        Map<String, Object> params,
        String eventCode,
        Long eventNumber,
        String workflowExecutionId) {
      this.params = params;
      this.eventCode = eventCode;
      this.eventNumber = eventNumber;
      this.workflowExecutionId = workflowExecutionId;
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

  @Json
  @Builder(toBuilder = true)
  public record TransactionOutput(Map<String, Object> result, String status)
      implements TransactionResult {

    public static TransactionOutput empty() {
      return new TransactionOutput(Collections.emptyMap());
    }

    public TransactionOutput(Map<String, Object> result) {
      this(result, "SUCCESS");
    }

    public TransactionOutput(Map<String, Object> result, String status) {
      this.result = result;
      this.status = status;
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
