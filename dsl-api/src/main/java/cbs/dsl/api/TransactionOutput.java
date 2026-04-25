package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.Map;

/** TransactionOutput wraps the execution result and status. */
@Json
public class TransactionOutput {
  private final Map<String, Object> result;
  private final String status;

  public TransactionOutput(Map<String, Object> result) {
    this(result, "SUCCESS");
  }

  public TransactionOutput(Map<String, Object> result, String status) {
    this.result = result;
    this.status = status;
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
