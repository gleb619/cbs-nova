package cbs.dsl.api;

import io.avaje.jsonb.Json;

import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Output carrier for event execution. Carries the enriched context and transaction results as
 * JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EventOutput {
  private Map<String, Object> context;
  private Map<String, Map<String, Object>> transactionResults;
  private String status;

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
