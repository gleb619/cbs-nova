package cbs.dsl.api.context;

import java.util.Map;
import java.util.function.BiFunction;

@FunctionalInterface
public interface EventEvaluator extends BiFunction<String, Map<String, Object>, Object> {

  <U> U evaluate(String code, Map<String, Object> params);

  @Override
  default Object apply(String code, Map<String, Object> params) {
    return evaluate(code, params);
  }
}
