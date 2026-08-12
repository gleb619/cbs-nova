package cbs.nova.dsl.utils;

import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Evaluates a mixed-text expression against a variable map.
 *
 * <p>
 * Implementations may support interpolation ({@code {name}}) and/or arithmetic/string expressions
 * ({@code ${a + b}}).
 */
@FunctionalInterface
public interface ExpressionEvaluator {

  @NonNull
  Object evaluate(@NonNull String expression, @NonNull Map<String, Object> variables);
}
