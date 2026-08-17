package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.AbstractBigDecimalAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class SimpleExpressionEvaluatorTest {

  private final SimpleExpressionEvaluator evaluator = new SimpleExpressionEvaluator();

  private Object eval(String expression) {
    return evaluator.evaluate(expression, Map.of());
  }

  private Object eval(String expression, Map<String, Object> variables) {
    return evaluator.evaluate(expression, variables);
  }

  private static AbstractBigDecimalAssert<?> assertBigDecimal(Object value) {
    return assertThat((BigDecimal) value);
  }

  @Nested
  @DisplayName("Interpolation")
  class Interpolation {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "'{name}', 'world'",
        "'Hello {name}!', 'Hello world!'",
        "'x{name}x', 'xworldx'",
        "'{a}{b}{c}', '123'",
        "'{missing}', ''"
    })
    void interpolationMatrix(String expression, String expected) {
      Map<String, Object> vars = Map.of("name", "world", "a", 1, "b", 2, "c", 3);
      assertThat(eval(expression, vars)).isEqualTo(expected);
    }

    @Test
    void singlePlaceholderReturnsTypedValueNotStringified() {
      Object result = eval("{name}", Map.of("name", 42));
      assertThat(result).isEqualTo(42).isInstanceOf(Integer.class);
    }

    @Test
    void singleDollarPlaceholderReturnsTypedValue() {
      Object result = eval("${name}", Map.of("name", 42));
      assertThat(result).isEqualTo(42).isInstanceOf(Integer.class);
    }

    @Test
    void mixedTextKeepsNonPlaceholderTextVerbatim() {
      assertThat(eval("  Hello {name}!  ", Map.of("name", "world")))
              .isEqualTo("  Hello world!  ");
    }

    @Test
    void singleDollarExpressionReturnsEvaluatedTypedResult() {
      Object result = eval("${1 + 2}");
      assertThat(result).isInstanceOf(BigDecimal.class);
      assertBigDecimal(result).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void multiplePlaceholdersSubstitutedInOrder() {
      Map<String, Object> vars = Map.of("a", 1, "b", 2, "c", 3);
      assertThat(eval("{a}-{b}-{c}", vars)).isEqualTo("1-2-3");
    }

    @Test
    void mixedBraceAndDollarPlaceholdersSubstitutedInOrder() {
      Map<String, Object> vars = Map.of("a", 1, "b", 2, "c", 4);
      assertThat(eval("sum: ${a + b}, {c}", vars)).isEqualTo("sum: 3, 4");
    }

    @Test
    void leadingDollarPlaceholderAtPositionZeroKeepsLiteralDollar() {
      Map<String, Object> vars = Map.of("a", 1, "b", 2);
      assertThat(eval("${a} + {b} = ${a + b}", vars)).isEqualTo("$1 + 2 = 3");
    }
  }

  @Nested
  @DisplayName("Variable resolution")
  class VariableResolution {

    @Test
    void presentKeyReturnsItsValue() {
      assertThat(eval("{count}", Map.of("count", 7))).isEqualTo(7);
    }

    @Test
    void nullValueRendersEmptyString() {
      Map<String, Object> vars = Collections.singletonMap("k", null);
      assertThat(eval("{k}", vars)).isEqualTo("");
      assertThat(eval("x{k}y", vars)).isEqualTo("xy");
    }

    @Test
    void absentKeyReturnsEmptyStringWithoutThrowing() {
      assertThat(eval("{missing}")).isEqualTo("");
      assertThat(eval("${missing}")).isEqualTo("");
      assertThat(eval("a {missing} b")).isEqualTo("a  b");
    }

    @Test
    void variablesReferencedInsideExpressions() {
      Map<String, Object> vars = Map.of("a", 2, "b", 3);
      assertBigDecimal(eval("${a + b}", vars)).isEqualByComparingTo(new BigDecimal("5"));
      assertBigDecimal(eval("${a / b}", vars))
              .isEqualByComparingTo(new BigDecimal("0.6666666666666667"));
    }
  }

  @Nested
  @DisplayName("Arithmetic")
  class Arithmetic {

    @ParameterizedTest(name = "${0} = {1}")
    @CsvSource({
        "'1 + 2', '3'",
        "'5 - 3', '2'",
        "'3 * 4', '12'",
        "'10 / 4', '2.5'",
        "'1 / 2', '0.5'",
        "'7 / 2', '3.5'",
        "'1 / 3', '0.3333333333333333'",
        "'2 + 3 * 4', '14'",
        "'(2 + 3) * 4', '20'",
        "'2.5 * 2', '5'",
        "'1.5 + 1.5', '3'"
    })
    void binaryOperationsReturnCorrectBigDecimal(String expression, String expected) {
      assertBigDecimal(eval("${" + expression + "}"))
              .isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void divisionUsesMathContextDecimal64() {
      assertBigDecimal(eval("${1 / 3}")).isEqualByComparingTo(new BigDecimal("0.3333333333333333"));
      assertBigDecimal(eval("${1 / 8}")).isEqualByComparingTo(new BigDecimal("0.125"));
    }

    @Test
    void precedenceMultiplicationBeforeAddition() {
      assertBigDecimal(eval("${2 + 3 * 4}")).isEqualByComparingTo(new BigDecimal("14"));
    }

    @Test
    void parenthesesOverridePrecedence() {
      assertBigDecimal(eval("${(2 + 3) * 4}")).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void unaryMinusInsideExpression() {
      assertBigDecimal(eval("${-3}")).isEqualByComparingTo(new BigDecimal("-3"));
      assertBigDecimal(eval("${-a}", Map.of("a", 5))).isEqualByComparingTo(new BigDecimal("-5"));
      assertBigDecimal(eval("${-(3 + 4)}")).isEqualByComparingTo(new BigDecimal("-7"));
    }

    @Test
    void unaryMinusInMixedTextRendersLiteralMinus() {
      assertThat(eval("-${a}", Map.of("a", 5))).isEqualTo("-5");
    }

    @Test
    void nestedUnaryChains() {
      assertBigDecimal(eval("${--3}")).isEqualByComparingTo(new BigDecimal("3"));
      assertBigDecimal(eval("${---3}")).isEqualByComparingTo(new BigDecimal("-3"));
      assertBigDecimal(eval("${+3}")).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void stripTrailingZerosAppliedToArithmeticResults() {
      assertBigDecimal(eval("${2.50 + 0}")).isEqualByComparingTo(new BigDecimal("2.5"));
      assertBigDecimal(eval("${2.5 * 2}")).isEqualByComparingTo(new BigDecimal("5"));
    }
  }

  @Nested
  @DisplayName("String semantics")
  class StringSemantics {

    @Test
    void plusConcatenatesTwoStringLiterals() {
      assertThat(eval("${'a' + 'b'}")).isEqualTo("ab");
      assertThat(eval("${'a' + \"b\"}")).isEqualTo("ab");
      assertThat(eval("${\"a\" + \"b\"}")).isEqualTo("ab");
    }

    @Test
    void plusConcatenatesStringAndNumber() {
      assertThat(eval("${'x' + 1}")).isEqualTo("x1");
      assertThat(eval("${1 + 'x'}")).isEqualTo("1x");
    }

    @Test
    void stringOperandForcesConcatenationOverAddition() {
      assertThat(eval("${a + 1}", Map.of("a", "5"))).isEqualTo("51");
    }

    @Test
    void numericStringConvertedForOtherOperators() {
      assertBigDecimal(eval("${'5' * 2}")).isEqualByComparingTo(new BigDecimal("10"));
      assertBigDecimal(eval("${'10' - 4}")).isEqualByComparingTo(new BigDecimal("6"));
      assertBigDecimal(eval("${'10' / 4}")).isEqualByComparingTo(new BigDecimal("2.5"));
    }

    @Test
    void stringLiteralsPreserveInnerSpaces() {
      assertThat(eval("${'hello world'}")).isEqualTo("hello world");
      assertThat(eval("${\"a b\"}")).isEqualTo("a b");
    }

    @Test
    void nonNumericStringInArithmeticThrows() {
      assertThatThrownBy(() -> eval("${'abc' - 1}"))
              .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> eval("${'abc' * 2}"))
              .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Rendering")
  class Rendering {

    @Test
    void bigDecimalRenderedViaStripTrailingZerosPlainString() {
      assertThat(eval("v=${2.50}")).isEqualTo("v=2.5");
      assertThat(eval("v=${2.5 * 2}")).isEqualTo("v=5");
      assertThat(eval("one third is ${1 / 3}")).isEqualTo("one third is 0.3333333333333333");
    }

    @Test
    void bigDecimalVariableRenderedStrippedInMixedText() {
      assertThat(eval("v={v}", Map.of("v", new BigDecimal("2.50")))).isEqualTo("v=2.5");
    }

    @Test
    void singleBracePlaceholderReturnsTypedBigDecimalWithoutRendering() {
      Object result = eval("{v}", Map.of("v", new BigDecimal("2.50")));
      assertThat(result).isInstanceOf(BigDecimal.class).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void singleDollarExpressionReturnsTypedBigDecimalNotStringified() {
      Object result = eval("${2.50}");
      assertThat(result).isInstanceOf(BigDecimal.class);
      assertBigDecimal(result).isEqualByComparingTo(new BigDecimal("2.5"));
      assertThat(((BigDecimal) result).scale()).isEqualTo(2);
    }

    @Test
    void nonBigDecimalValuesRenderedWithStringValue() {
      assertThat(eval("flag={flag}", Map.of("flag", true))).isEqualTo("flag=true");
    }

    @Test
    void replacementNotExpandedAsRegexOrGroupReference() {
      assertThat(eval("{v}", Map.of("v", "a$1\\b"))).isEqualTo("a$1\\b");
    }
  }

  @Nested
  @DisplayName("Sandbox boundary")
  class SandboxBoundary {

    @Test
    void unterminatedStringLiteralThrows() {
      assertThatThrownBy(() -> eval("${'abc}"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unterminated string literal");
      assertThatThrownBy(() -> eval("${\"abc}"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unterminated string literal");
    }

    @ParameterizedTest(name = "rejects unexpected character: {0}")
    @CsvSource({
        "'${a @ b}'",
        "'${1 = 2}'",
        "'${#hash}'",
        "'${a ^ b}'",
        "'${a ~ b}'"
    })
    void unexpectedCharacterRejected(String expression) {
      assertThatThrownBy(() -> eval(expression))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unexpected character");
    }

    @Test
    void missingClosingParenthesisThrows() {
      assertThatThrownBy(() -> eval("${(1 + 2}"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Missing closing parenthesis");
    }

    @ParameterizedTest(name = "unexpected end of expression: {0}")
    @CsvSource({
        "'${1 + }'",
        "'${ }'",
        "'${a +}'"
    })
    void unexpectedEndOfExpressionThrows(String expression) {
      assertThatThrownBy(() -> eval(expression))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unexpected end of expression");
    }

    @Test
    void unexpectedTokenThrows() {
      assertThatThrownBy(() -> eval("${)}"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unexpected token");
    }

    @ParameterizedTest(name = "trailing tokens rejected: {0}")
    @CsvSource({
        "'${1 2}'",
        "'${a b}'"
    })
    void trailingTokensAfterCompleteExpressionThrows(String expression) {
      assertThatThrownBy(() -> eval(expression))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Unexpected token in expression");
    }

    @Test
    void nonNumericWhereNumberRequiredThrows() {
      Map<String, Object> vars = Map.of("list", List.of("x"), "flag", true);
      assertThatThrownBy(() -> eval("${list + 1}", vars))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Cannot convert");
      assertThatThrownBy(() -> eval("${flag * 2}", vars))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Cannot convert");
    }

    @Test
    void noReflectionOrExternalCallPathReachable() {
      assertThatThrownBy(() -> eval("${System.exit(0)}"))
              .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> eval("${Class.forName('java.lang.Runtime')}"))
              .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> eval("${getClass()}"))
              .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> eval("${process.env}"))
              .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maliciousLookingIdentifiersReturnLiterallyWithoutExecuting() {
      assertThat(eval("${$}")).isEqualTo("");
      assertThat(eval("${null}")).isEqualTo("");
      assertThat(eval("${undefined}")).isEqualTo("");
      assertThat(eval("x${$}y")).isEqualTo("xy");
    }
  }
}
