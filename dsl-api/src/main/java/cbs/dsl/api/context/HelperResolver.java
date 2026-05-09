package cbs.dsl.api.context;

import java.util.Map;
import java.util.function.BiFunction;

@FunctionalInterface
public interface HelperResolver extends BiFunction<String, Map<String, Object>, Object> {

  <U> U run(String key, Map<String, Object> values);

  @Override
  default Object apply(String key, Map<String, Object> values) {
    return run(key, values);
  }

}
