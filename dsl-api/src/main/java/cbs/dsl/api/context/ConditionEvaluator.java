package cbs.dsl.api.context;

import cbs.dsl.api.ConditionTypes.ConditionOutput;
import java.util.Map;
import java.util.function.BiFunction;

@FunctionalInterface
public interface ConditionEvaluator extends BiFunction<String, Map<String, Object>, ConditionOutput> {

  ConditionOutput evaluate(String code, Map<String, Object> params);

  @Override
  default ConditionOutput apply(String code, Map<String, Object> params) {
    return evaluate(code, params);
  }
}
