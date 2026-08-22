package cbs.nova.starter.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.utils.SimpleExpressionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Cross-evaluator contract: expressions that must behave identically whether the runtime uses
 * {@link SimpleExpressionEvaluator} (platform default) or {@link MvelExpressionEvaluator} (starter
 * autoconfiguration).
 *
 * <p>
 * The two evaluators can return different Java types for the same numeric expression (BigDecimal
 * from Simple, Integer/Double from MVEL). The contract therefore compares results semantically:
 * numbers are compared by numeric value, {@link JsonValue} is unwrapped, null/empty are equivalent
 * in interpolation contexts, and everything else uses {@link Object#equals(Object)}.
 */
class ExpressionEvaluatorContractTest {

  @SuppressWarnings("removal")
  private final SimpleExpressionEvaluator simple = new SimpleExpressionEvaluator();

  private final MvelExpressionEvaluator mvel = new MvelExpressionEvaluator();

  private Object simple(String expression, Map<String, Object> variables) {
    return simple.evaluate(expression, variables);
  }

  private Object mvel(String expression, Map<String, Object> variables) {
    return mvel.evaluate(expression, variables);
  }

  /**
   * Compares evaluator results semantically. Numeric values may have different types (BigDecimal vs
   * Integer/Double) but must represent the same number. {@link JsonValue} results are unwrapped to
   * their scalar value. In interpolation contexts a missing or null variable renders as an empty
   * string, so null and "" are treated as equivalent.
   */
  private static boolean equivalent(Object left, Object right) {
    Object a = unwrap(left);
    Object b = unwrap(right);
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
  @MethodSource("identicalResultsCases")
  @DisplayName("Both evaluators produce semantically identical results")
  void bothEvaluatorsProduceIdenticalResults(String description, String expression,
          Map<String, Object> variables, Object expected) {
    Object simpleResult = simple(expression, variables);
    Object mvelResult = mvel(expression, variables);

    assertThat(simpleResult)
            .as("Simple result for '%s' (%s)", expression, description)
            .satisfies(r -> equivalent(r, expected));
    assertThat(mvelResult)
            .as("MVEL result for '%s' (%s)", expression, description)
            .satisfies(r -> equivalent(r, expected));
    assertThat(simpleResult)
            .as("Cross-evaluator match for '%s' (%s)", expression, description)
            .satisfies(r -> equivalent(r, mvelResult));
  }

  static Stream<Arguments> identicalResultsCases() {
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

            // Numeric arithmetic (compared semantically because types differ)
            Arguments.of("integer addition", "${a + b}", Map.of("a", 2, "b", 3), 5),
            Arguments.of("integer subtraction", "${a - b}", Map.of("a", 5, "b", 3), 2),
            Arguments.of("integer multiplication", "${a * b}", Map.of("a", 3, "b", 4), 12),
            Arguments.of("integer division", "${a / b}", Map.of("a", 10, "b", 4), 2.5),
            Arguments.of("operator precedence", "${2 + 3 * 4}", Map.of(), 14),
            Arguments.of("parentheses override precedence", "${(2 + 3) * 4}", Map.of(), 20),
            Arguments.of("unary minus", "${-a}", Map.of("a", 5), -5),
            Arguments.of("decimal literal arithmetic", "${2.5 * 2}", Map.of(), 5),

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
                    true));
  }

  @Nested
  @DisplayName("Known dialect divergences")
  class KnownDivergences {

    /**
     * Cases where both evaluators succeed but return different, legitimate values. The contract
     * documents each behavior explicitly rather than forcing a match.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cbs.nova.starter.expression.ExpressionEvaluatorContractTest#successDivergences")
    void documentSuccessfulDivergence(String description, String expression,
            Map<String, Object> variables, Object simpleExpected, Object mvelExpected) {
      Object simpleResult = simple(expression, variables);
      Object mvelResult = mvel(expression, variables);

      assertThat(simpleResult)
              .as("Simple behavior for '%s'", expression)
              .satisfies(r -> equivalent(r, simpleExpected));
      assertThat(mvelResult)
              .as("MVEL behavior for '%s'", expression)
              .satisfies(r -> equivalent(r, mvelExpected));
    }

    /**
     * The SimpleExpressionEvaluator is intentionally sandboxed and only supports +, -, *, / and
     * parentheses. MVEL supports a full expression language. These tests pin the boundary.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cbs.nova.starter.expression.ExpressionEvaluatorContractTest#unsupportedInSimpleCases")
    void simpleRejectsExpressionThatMvelSupports(String description, String expression,
            Map<String, Object> variables, Object mvelExpected) {
      assertThatThrownBy(() -> simple(expression, variables))
              .as("Simple should reject '%s'", expression)
              .isInstanceOf(RuntimeException.class);

      Object mvelResult = mvel(expression, variables);
      assertThat(mvelResult)
              .as("MVEL result for '%s'", expression)
              .satisfies(r -> equivalent(r, mvelExpected));
    }

    /**
     * The platform's SimpleExpressionEvaluator supports a JsonValue-specific path syntax
     * ({@code {var.json().path}} and {@code ${var.json().path}}). MVEL has no special knowledge of
     * that syntax. In brace interpolation it treats the whole path as an unknown variable name and
     * renders empty; in dollar expressions it attempts to evaluate it as MVEL and fails.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cbs.nova.starter.expression.ExpressionEvaluatorContractTest#jsonPathDivergenceCases")
    void jsonPathSyntaxDiverges(String description, String expression,
            Map<String, Object> variables, Object simpleExpected, Object mvelExpected) {
      Object simpleResult = simple(expression, variables);
      assertThat(simpleResult)
              .as("Simple supports JsonValue path syntax for '%s'", expression)
              .satisfies(r -> equivalent(r, simpleExpected));

      if (mvelExpected instanceof Class<?>) {
        assertThatThrownBy(() -> mvel(expression, variables))
                .as("MVEL should fail for '%s'", expression)
                .isInstanceOf((Class<?>) mvelExpected);
      } else {
        Object mvelResult = mvel(expression, variables);
        assertThat(mvelResult)
                .as("MVEL behavior for '%s'", expression)
                .satisfies(r -> equivalent(r, mvelExpected));
      }
    }
  }

  static Stream<Arguments> successDivergences() {
    return Stream.of(
            // The "null" literal is not a keyword in SimpleExpressionEvaluator; it is treated
            // as an identifier that resolves to the empty string. MVEL evaluates the null
            // literal, and the evaluator then coerces it back to empty for a top-level result.
            Arguments.of("null literal in single expression", "${null}", Map.of(), "", ""),

            // MVEL does not strip trailing zeros for numeric expression results, while Simple
            // always returns BigDecimal. Numeric equivalence is the practical contract.
            Arguments.of("decimal result types", "${2.50 + 0}", Map.of(),
                    new BigDecimal("2.5"), 2.5d));
  }

  static Stream<Arguments> unsupportedInSimpleCases() {
    return Stream.of(
            Arguments.of("string equality", "${name == 'world'}", Map.of("name", "world"),
                    true),
            Arguments.of("string inequality", "${name != 'world'}", Map.of("name", "world"),
                    false),
            Arguments.of("numeric less-than", "${count < 10}", Map.of("count", 7), true),
            Arguments.of("numeric greater-than", "${count > 10}", Map.of("count", 7), false),
            Arguments.of("boolean conjunction", "${flag && true}", Map.of("flag", true), true),
            Arguments.of("boolean disjunction", "${flag || false}", Map.of("flag", false),
                    false),
            Arguments.of("null check", "${name == null}", singleton("name", null), true));
  }

  static Stream<Arguments> jsonPathDivergenceCases() {
    String body = "{\"items\":[{\"id\":1},{\"id\":2}]}";
    return Stream.of(
            Arguments.of("json path in brace placeholder", "{body.json().items[0].id}",
                    Map.of("body", body), 1, ""),
            Arguments.of("json path in dollar expression", "${body.json().items[0].id}",
                    Map.of("body", body), 1, RuntimeException.class));
  }

  private static Map<String, Object> singleton(String key, Object value) {
    Map<String, Object> map = new HashMap<>();
    map.put(key, value);
    return Collections.unmodifiableMap(map);
  }
}
