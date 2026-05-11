package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record HelperContext(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperEvaluator helperEvaluator) {

  public HelperContext put(String key, Object value) {
    params.put(key, value);
    return this;
  }

  public Object get(String key) {
    return params.get(key);
  }

  public Object helper(String key, Map<String, Object> values) {
    return helperEvaluator.evaluate(key, values);
  }

  public HelperContext copy() {
    return toBuilder().build();
  }
}
