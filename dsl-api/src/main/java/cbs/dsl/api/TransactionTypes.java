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

  }

  @Json
  @Builder(toBuilder = true)
  public record TransactionOutput(Map<String, Object> params, String status)
      implements TransactionResult {

    public static TransactionOutput success(Map<String, Object> params) {
      return new TransactionOutput(params, "SUCCESS");
    }

    public static TransactionOutput empty() {
      return new TransactionOutput(Collections.emptyMap(), "ERROR");
    }

  }
}
