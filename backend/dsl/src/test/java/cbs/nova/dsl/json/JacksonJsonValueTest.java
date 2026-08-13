package cbs.nova.dsl.json;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class JacksonJsonValueTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private JsonValue parse(String json) {
    return JsonValues.of(json, mapper);
  }

  @Test
  void nestedObjectAccess() {
    JsonValue value = parse("{\"a\":{\"b\":{\"c\":1}}}");
    assertThat(value.get("a").get("b").get("c").asInt()).isEqualTo(1);
  }

  @Test
  void arrayIndexAccess() {
    JsonValue value = parse("{\"items\":[{\"id\":7},{\"id\":9}]}");
    assertThat(value.get("items").get(0).get("id").asInt()).isEqualTo(7);
    assertThat(value.get("items").get(1).get("id").asInt()).isEqualTo(9);
  }

  @Test
  void missingPathIsNotPresent() {
    JsonValue value = parse("{\"a\":\"x\"}");
    JsonValue missing = value.get("b");
    assertThat(missing.isPresent()).isFalse();
    assertThat(missing.asString()).isNull();
  }

  @Test
  void typeRetrieval() {
    JsonValue value = parse(
            "{\"s\":\"hello\",\"n\":42,\"d\":3.14,\"l\":9007199254740993,\"b\":true,\"nil\":null}");
    assertThat(value.get("s").asString()).isEqualTo("hello");
    assertThat(value.get("n").asInt()).isEqualTo(42);
    assertThat(value.get("n").asLong()).isEqualTo(42L);
    assertThat(value.get("d").asDouble()).isEqualTo(3.14);
    assertThat(value.get("l").asLong()).isEqualTo(9007199254740993L);
    assertThat(value.get("b").asBoolean()).isTrue();
    assertThat(value.get("nil").isNull()).isTrue();
    assertThat(value.get("nil").asString()).isNull();
  }

  @Test
  void numericAsDecimal() {
    JsonValue value = parse("{\"n\":2.50}");
    assertThat(value.get("n").asDecimal()).isEqualByComparingTo(new BigDecimal("2.50"));
  }

  @Test
  void asListAndAsMap() {
    JsonValue value = parse(
            "{\"items\":[{\"id\":1},{\"id\":2}],\"obj\":{\"a\":1,\"b\":2}}");
    assertThat(value.get("items").asList()).hasSize(2);
    assertThat(value.get("items").asList().get(0).get("id").asInt()).isEqualTo(1);
    assertThat(value.get("obj").asMap()).containsKeys("a", "b");
  }

  @Test
  void convertsMapAndListToJsonValue() {
    JsonValue value = JsonValues.of(Map.of("items", List.of(Map.of("id", 1))), mapper);
    assertThat(value.get("items").get(0).get("id").asInt()).isEqualTo(1);
  }

  @Test
  void blankOrNullStringIsMissing() {
    assertThat(JsonValues.of("", mapper).isPresent()).isFalse();
    assertThat(JsonValues.of(null, mapper).isPresent()).isFalse();
  }

  @Test
  void invalidJsonThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> JsonValues.of("not json", mapper));
  }

  @Test
  void rawReturnsUnderlyingJsonNode() {
    JsonValue value = parse("{\"a\":1}");
    assertThat(value.raw()).isInstanceOf(com.fasterxml.jackson.databind.JsonNode.class);
  }
}
