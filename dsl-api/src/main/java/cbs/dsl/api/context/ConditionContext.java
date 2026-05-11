package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record ConditionContext(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperEvaluator helperEvaluator,
    ConditionEvaluator conditionEvaluator,
    boolean result) {

  public ConditionContext put(String key, Object value) {
    params.put(key, value);
    return this;
  }

  public Object get(String key) {
    return params.get(key);
  }

  public Object helper(String key, Map<String, Object> values) {
    return helperEvaluator.evaluate(key, values);
  }

  public Object condition(String code, Map<String, Object> values) {
    return conditionEvaluator.evaluate(code, values);
  }

  public ConditionContext result(boolean value) {
    return toBuilder().result(value).build();
  }

  public ConditionContext copy() {
    return toBuilder().build();
  }
}
