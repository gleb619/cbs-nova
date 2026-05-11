package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record EventContext(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperEvaluator helperEvaluator,
    TransactionEvaluator transactionEvaluator) {

  public EventContext copy() {
    return toBuilder().build();
  }

}
