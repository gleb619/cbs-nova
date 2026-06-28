package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record TransactionContext(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperEvaluator helperEvaluator,
    TransactionEvaluator transactionEvaluator) {

  public TransactionContext put(String key, Object value) {
    params.put(key, value);
    return this;
  }

  public Object get(String key) {
    return params.get(key);
  }

  public TransactionContext copy() {
    return toBuilder().build();
  }

  public Object runTransaction(String code, Map<String, Object> values) {
    return transactionEvaluator.evaluate(code, values);
  }
}
