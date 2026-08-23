package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests for the platform default {@link MvelExpressionEvaluator}.
 */
@DisplayName("MvelExpressionEvaluator")
class MvelExpressionEvaluatorTest {

  private final MvelExpressionEvaluator evaluator = new MvelExpressionEvaluator();

  private Object eval(String expression, Map<String, Object> variables) {
    return evaluator.evaluate(expression, variables);
  }

  /**
   * Compares results semantically: numbers by numeric value, JsonValue unwrapped, null/empty
   * equivalent in interpolation contexts.
   */
  private static boolean equivalent(Object actual, Object expected) {
    Object a = unwrap(actual);
    Object b = unwrap(expected);
    if (a == null || b == null) {
      return (a == null && b == null)
              || (String.valueOf(a).isEmpty() && String.valueOf(b).isEmpty());
    }
    if (a instanceof Number ln && b instanceof Number rn) {
      return toBigDecimal(ln).compareTo(toBigDecimal(rn)) == 0;
    }
    return a.equals(b);
  }

  private static Object unwrap(Object value) {
    if (value instanceof JsonValue jv && jv.isPresent()) {
      if (jv.isNull()) {
        return null;
      }
      BigDecimal decimal = jv.asDecimal();
      if (decimal != null) {
        return decimal;
      }
      Boolean bool = jv.asBoolean();
      if (bool != null) {
        return bool;
      }
      String string = jv.asString();
      if (string != null) {
        return string;
      }
    }
    return value;
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Double d) {
      return BigDecimal.valueOf(d);
    }
    if (value instanceof Float f) {
      return BigDecimal.valueOf(f);
    }
    return new BigDecimal(value.toString());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("expressionCases")
  @DisplayName("evaluates supported expressions")
  void evaluatesSupportedExpressions(String description, String expression,
          Map<String, Object> variables, Object expected) {
    assertThat(eval(expression, variables))
            .as("%s -> '%s'", description, expression)
            .satisfies(r -> equivalent(r, expected));
  }

  static Stream<Arguments> expressionCases() {
    return Stream.of(
            // Interpolation
            Arguments.of("plain variable interpolation", "{name}", Map.of("name", "world"),
                    "world"),
            Arguments.of("single dollar placeholder returns typed value", "${name}",
                    Map.of("name", 42), 42),
            Arguments.of("mixed text interpolation", "Hello {name}!",
                    Map.of("name", "world"), "Hello world!"),
            Arguments.of("multiple placeholders", "{a}-{b}-{c}", Map.of("a", 1, "b", 2, "c", 3),
                    "1-2-3"),
            Arguments.of("mixed brace and dollar placeholders", "sum: ${a + b}, {c}",
                    Map.of("a", 1, "b", 2, "c", 4), "sum: 3, 4"),

            // Missing / null variables in interpolation
            Arguments.of("missing brace placeholder renders empty", "{missing}", Map.of(), ""),
            Arguments.of("missing dollar placeholder renders empty", "${missing}", Map.of(), ""),
            Arguments.of("null brace placeholder renders empty", "{k}", singleton("k", null),
                    ""),
            Arguments.of("null dollar placeholder renders empty", "${k}", singleton("k", null),
                    ""),
            Arguments.of("missing placeholder in mixed text", "a {missing} b", Map.of(),
                    "a  b"),

            // Numeric arithmetic
            Arguments.of("integer addition", "${a + b}", Map.of("a", 2, "b", 3), 5),
            Arguments.of("integer subtraction", "${a - b}", Map.of("a", 5, "b", 3), 2),
            Arguments.of("integer multiplication", "${a * b}", Map.of("a", 3, "b", 4), 12),
            Arguments.of("integer division", "${a / b}", Map.of("a", 10, "b", 4), 2.5),
            Arguments.of("operator precedence", "${2 + 3 * 4}", Map.of(), 14),
            Arguments.of("parentheses override precedence", "${(2 + 3) * 4}", Map.of(), 20),
            Arguments.of("unary minus", "${-a}", Map.of("a", 5), -5),
            Arguments.of("decimal literal arithmetic", "${2.5 * 2}", Map.of(), 5.0),

            // String concatenation
            Arguments.of("concatenate string literals", "${'a' + 'b'}", Map.of(), "ab"),
            Arguments.of("concatenate string and number", "${'x' + 1}", Map.of(), "x1"),
            Arguments.of("concatenate number and string", "${1 + 'x'}", Map.of(), "1x"),
            Arguments.of("string operand forces concatenation", "${a + 1}", Map.of("a", "5"),
                    "51"),

            // Rendering
            Arguments.of("big decimal stripped in mixed text", "v={v}",
                    Map.of("v", new BigDecimal("2.50")), "v=2.5"),
            Arguments.of("boolean rendered in mixed text", "flag={flag}", Map.of("flag", true),
                    "flag=true"),
            Arguments.of("big decimal expression rendered stripped", "v=${2.50}", Map.of(),
                    "v=2.5"),

            // Variable access
            Arguments.of("variable inside expression", "${a + b}", Map.of("a", 2, "b", 3), 5),
            Arguments.of("boolean variable as expression result", "${flag}", Map.of("flag", true),
                    true),

            // MVEL-specific features now available in the platform default
            Arguments.of("string equality", "${name == 'world'}", Map.of("name", "world"), true),
            Arguments.of("string inequality", "${name != 'world'}", Map.of("name", "world"),
                    false),
            Arguments.of("numeric less-than", "${count < 10}", Map.of("count", 7), true),
            Arguments.of("boolean conjunction", "${flag && true}", Map.of("flag", true), true),
            Arguments.of("null check", "${name == null}", singleton("name", null), true));
  }

  @Test
  @DisplayName("top-level null expression renders empty")
  void nullLiteralRendersEmpty() {
    assertThat(eval("${null}", Map.of())).isEqualTo("");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("unsupportedJsonPathCases")
  @DisplayName("does not support JsonValue .json() path syntax")
  void doesNotSupportJsonPathSyntax(String description, String expression,
          Map<String, Object> variables) {
    assertThatThrownBy(() -> eval(expression, variables))
            .as("%s should throw", description)
            .isInstanceOf(RuntimeException.class);
  }

  static Stream<Arguments> unsupportedJsonPathCases() {
    String body = "{\"items\":[{\"id\":1},{\"id\":2}]}";
    return Stream.of(
            Arguments.of("dollar expression with json path", "${body.json().items[0].id}",
                    Map.of("body", body)));
  }

  private static Map<String, Object> singleton(String key, Object value) {
    Map<String, Object> map = new HashMap<>();
    map.put(key, value);
    return Collections.unmodifiableMap(map);
  }
}
