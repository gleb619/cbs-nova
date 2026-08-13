package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.json.JsonValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@DisplayName("SimpleExpressionEvaluator JSON integration")
class SimpleExpressionEvaluatorJsonTest {

  private final SimpleExpressionEvaluator evaluator = new SimpleExpressionEvaluator();
  private final ObjectMapper mapper = new ObjectMapper();

  private Object eval(String expression, Map<String, Object> variables) {
    return evaluator.evaluate(expression, variables);
  }

  @Test
  void interpolationNavigatesJsonPath() {
    Map<String, Object> vars = Map.of("body", "{\"items\":[{\"id\":7},{\"id\":9}]}");
    Object result = eval("{body.json().items[0].id}", vars);
    assertThat(result).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) result).asInt()).isEqualTo(7);
    assertThat(eval("id={body.json().items[0].id}", vars)).isEqualTo("id=7");
  }

  @Test
  void dollarExpressionNavigatesJsonPath() {
    Map<String, Object> vars = Map.of("body", "{\"count\":5}");
    assertThat(eval("${body.json().count + 1}", vars)).isEqualTo(new BigDecimal("6"));
  }

  @Test
  void jsonPathInsideStringConcat() {
    Map<String, Object> vars = Map.of("body", "{\"name\":\"world\"}");
    assertThat(eval("${'hello ' + body.json().name}", vars)).isEqualTo("hello world");
  }

  @Test
  void missingJsonPathRendersEmptyInInterpolation() {
    Map<String, Object> vars = Map.of("body", "{\"a\":\"x\"}");
    Object missing = eval("{body.json().b}", vars);
    assertThat(missing).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) missing).isPresent()).isFalse();
    assertThat(eval("x{body.json().b}y", vars)).isEqualTo("xy");
  }

  @Test
  void missingJsonPathReturnsJsonValueInExpression() {
    Map<String, Object> vars = Map.of("body", "{\"a\":\"x\"}");
    Object result = eval("${body.json().b}", vars);
    assertThat(result).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) result).isPresent()).isFalse();
  }

  @Test
  void jsonBooleanAndNullTypes() {
    Map<String, Object> vars = Map.of("body", "{\"flag\":true,\"empty\":null}");
    Object flag = eval("{body.json().flag}", vars);
    assertThat(flag).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) flag).asBoolean()).isTrue();
    assertThat(((JsonValue) flag).asString()).isEqualTo("true");

    Object empty = eval("{body.json().empty}", vars);
    assertThat(((JsonValue) empty).isNull()).isTrue();
  }

  @Test
  void mapBodyCanBeAccessedAsJson() {
    Map<String, Object> vars = Map.of("body", Map.of("items", List.of(Map.of("id", 3))));
    Object result = eval("{body.json().items[0].id}", vars);
    assertThat(result).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) result).asInt()).isEqualTo(3);
  }

  @Test
  void jsonValueVariableNavigatesDirectly() {
    Map<String, Object> vars = Map.of("payload",
            JsonValues.of("{\"nested\":{\"value\":42}}", mapper));
    Object result = eval("{payload.json().nested.value}", vars);
    assertThat(result).isInstanceOf(JsonValue.class);
    assertThat(((JsonValue) result).asInt()).isEqualTo(42);
  }
}
