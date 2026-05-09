package cbs.dsl.api;

import cbs.dsl.api.TransactionFunction.TransactionArg;
import cbs.dsl.api.TransactionFunction.TransactionResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionTypes {

  @Json
  @Builder(toBuilder = true)
  public record TransactionInput(Map<String, Object> params, String eventNumber)
      implements TransactionArg {

    public TransactionOutput asOutput() {
      return TransactionOutput.success(params);
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record TransactionOutput(Map<String, Object> params, TransactionStatus status)
      implements TransactionResult {

    public static TransactionOutput success(Map<String, Object> params) {
      return new TransactionOutput(params, TransactionStatus.SUCCESS);
    }

    public static TransactionOutput error(Map<String, Object> params) {
      return new TransactionOutput(params, TransactionStatus.ERROR);
    }

    public static TransactionOutput empty() {
      return new TransactionOutput(Collections.emptyMap(), TransactionStatus.UNDEFINED);
    }
  }

  public enum TransactionStatus {
    UNDEFINED,
    SUCCESS,
    PENDING,
    ERROR,
  }
}
