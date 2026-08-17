package cbs.nova.starter.expression;

import cbs.nova.dsl.utils.ExpressionEvaluator;
import org.jspecify.annotations.NonNull;
import org.mvel2.MVEL;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ExpressionEvaluator} backed by MVEL.
 *
 * <p>
 * Supports the same interpolation patterns as {@code SimpleExpressionEvaluator}: plain variable
 * placeholders ({@code {name}}) and MVEL expressions ({@code ${a + b}}).
 */
public final class MvelExpressionEvaluator implements ExpressionEvaluator {

  private static final Pattern PLACEHOLDER = Pattern.compile("(\\$?\\{([^{}]+)\\})");

  @Override
  public @NonNull Object evaluate(@NonNull String expression,
          @NonNull Map<String, Object> variables) {
    Matcher matcher = PLACEHOLDER.matcher(expression);
    if (isSinglePlaceholder(expression, matcher)) {
      String inner = matcher.group(2).trim();
      if (matcher.group(1).startsWith("${")) {
        return evalExpression(inner, variables);
      }
      return resolveVariable(inner, variables);
    }
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String raw = matcher.group(1);
      String inner = matcher.group(2).trim();
      Object value;
      if (raw.startsWith("${")) {
        value = evalExpression(inner, variables);
      } else {
        value = resolveVariable(inner, variables);
      }
      String replacement = value == null ? raw : renderValue(value);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private boolean isSinglePlaceholder(@NonNull String expression, @NonNull Matcher matcher) {
    return matcher.matches();
  }

  private String renderValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private @NonNull Object resolveVariable(@NonNull String name,
          @NonNull Map<String, Object> variables) {
    if (variables.containsKey(name)) {
      Object value = variables.get(name);
      return value == null ? "" : value;
    }
    return "";
  }

  private @NonNull Object evalExpression(@NonNull String expression,
          @NonNull Map<String, Object> variables) {
    Object result = MVEL.eval(expression, variables);
    return result == null ? "" : result;
  }
}
