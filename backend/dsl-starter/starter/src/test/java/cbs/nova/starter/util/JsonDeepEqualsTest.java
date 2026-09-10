package cbs.nova.starter.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link JsonDeepEquals}. Locks in the documented normalization policy:
 *
 * <ul>
 * <li>object key order is irrelevant,
 * <li>array order is significant,
 * <li>missing key and null-valued key are distinct (both directions),
 * <li>numbers equal when their mathematical value is equal (e.g. {@code 1}, {@code 1.0},
 * {@code 1.00}).
 * </ul>
 */
class JsonDeepEqualsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode parse(String json) {
    try {
      return mapper.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("invalid test fixture: " + json, e);
    }
  }

  @Test
  void equalObjects() {
    JsonNode a = parse("{\"a\":1,\"b\":2}");
    JsonNode b = parse("{\"a\":1,\"b\":2}");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isTrue();
  }

  @Test
  void objectKeyOrderIsIrrelevant() {
    JsonNode a = parse("{\"a\":1,\"b\":2,\"c\":3}");
    JsonNode b = parse("{\"c\":3,\"a\":1,\"b\":2}");
    JsonNode c = parse("{\"b\":2,\"c\":3,\"a\":1}");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isTrue();
    assertThat(JsonDeepEquals.deepEquals(a, c)).isTrue();
  }

  @Test
  void differentObjectValuesAreNotEqual() {
    JsonNode a = parse("{\"a\":1}");
    JsonNode b = parse("{\"a\":2}");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isFalse();
  }

  @Test
  void missingKeyVsNullValueBothDirections() {
    JsonNode absentOnActual = parse("{\"a\":1}");
    JsonNode nullOnActual = parse("{\"a\":1,\"b\":null}");
    JsonNode absentOnExpected = parse("{\"a\":1}");
    JsonNode nullOnExpected = parse("{\"a\":1,\"b\":null}");

    assertThat(JsonDeepEquals.deepEquals(absentOnActual, nullOnExpected)).isFalse();
    assertThat(JsonDeepEquals.deepEquals(nullOnActual, absentOnExpected)).isFalse();
    assertThat(JsonDeepEquals.deepEquals(nullOnActual, nullOnExpected)).isTrue();
  }

  @Test
  void nestedObjectKeyOrderIsIrrelevant() {
    JsonNode a = parse("{\"outer\":{\"x\":1,\"y\":2}}");
    JsonNode b = parse("{\"outer\":{\"y\":2,\"x\":1}}");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isTrue();
  }

  @Test
  void equalArrays() {
    JsonNode a = parse("[1,2,3]");
    JsonNode b = parse("[1,2,3]");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isTrue();
  }

  @Test
  void arrayOrderIsSignificant() {
    JsonNode a = parse("[1,2,3]");
    JsonNode b = parse("[3,2,1]");
    JsonNode c = parse("[2,1,3]");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isFalse();
    assertThat(JsonDeepEquals.deepEquals(a, c)).isFalse();
  }

  @Test
  void differentArrayLengthsAreNotEqual() {
    JsonNode a = parse("[1,2]");
    JsonNode b = parse("[1,2,3]");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isFalse();
  }

  @Test
  void equalScalars() {
    assertThat(JsonDeepEquals.deepEquals(parse("\"foo\""), parse("\"foo\"")))
            .isTrue();
    assertThat(JsonDeepEquals.deepEquals(parse("true"), parse("true")))
            .isTrue();
    assertThat(JsonDeepEquals.deepEquals(parse("false"), parse("false")))
            .isTrue();
    assertThat(JsonDeepEquals.deepEquals(parse("null"), parse("null")))
            .isTrue();
  }

  @Test
  void differentScalarsAreNotEqual() {
    assertThat(JsonDeepEquals.deepEquals(parse("\"foo\""), parse("\"bar\"")))
            .isFalse();
    assertThat(JsonDeepEquals.deepEquals(parse("true"), parse("false")))
            .isFalse();
    assertThat(JsonDeepEquals.deepEquals(parse("null"), parse("\"null\"")))
            .isFalse();
  }

  @Test
  void typeMismatchIsNotEqual() {
    assertThat(JsonDeepEquals.deepEquals(parse("1"), parse("\"1\"")))
            .isFalse();
    assertThat(JsonDeepEquals.deepEquals(parse("{}"), parse("[]"))).isFalse();
    assertThat(JsonDeepEquals.deepEquals(parse("null"), parse("{}")))
            .isFalse();
  }

  @Test
  void numberNormalizationIntegerEqualsDecimal() {
    JsonNode intOne = parse("1");
    JsonNode decimalOne = parse("1.0");
    JsonNode decimalOneDoubleZero = parse("1.00");
    assertThat(JsonDeepEquals.deepEquals(intOne, decimalOne)).isTrue();
    assertThat(JsonDeepEquals.deepEquals(decimalOne, decimalOneDoubleZero)).isTrue();
    assertThat(JsonDeepEquals.deepEquals(intOne, decimalOneDoubleZero)).isTrue();
  }

  @Test
  void numberValuesThatDifferAreNotEqual() {
    JsonNode a = parse("1");
    JsonNode b = parse("2");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isFalse();
  }

  @Test
  void deeplyNestedMixedStructures() {
    JsonNode a = parse(
            "{\"items\":[{\"id\":1,\"meta\":{\"k\":\"v\"}},{\"id\":2}],\"count\":2}");
    JsonNode b = parse(
            "{\"count\":2,\"items\":[{\"meta\":{\"k\":\"v\"},\"id\":1},{\"id\":2}]}");
    assertThat(JsonDeepEquals.deepEquals(a, b)).isTrue();
  }
}
