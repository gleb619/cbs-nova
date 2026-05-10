package cbs.nova.model;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@Builder(toBuilder = true)
public record TransactionExecutionRequest(
    @NonNull String transactionCode,
    @NonNull String performedBy,
    @NonNull Map<String, Object> params) {

  public TransactionInput toTransactionInput(String eventNumber) {
    return TransactionInput.builder().eventNumber(eventNumber).params(params()).build();
  }
}
