package cbs.nova.dsl.utils;

import org.jspecify.annotations.NonNull;

import java.util.Map;

@FunctionalInterface
public interface ExpressionEvaluator {

  @NonNull
  Object evaluate(@NonNull String expression, @NonNull Map<String, Object> variables);
}
