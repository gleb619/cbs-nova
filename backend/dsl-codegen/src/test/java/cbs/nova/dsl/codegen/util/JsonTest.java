package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JsonTest {

  private final Json json = new Json();

  @Test
  void serializesPrimitives() {
    assertThat(json.write("hello")).isEqualTo("\"hello\"");
    assertThat(json.write(42)).isEqualTo("42");
    assertThat(json.write(true)).isEqualTo("true");
    assertThat(json.write(false)).isEqualTo("false");
  }

  @Test
  void serializesNestedMapAndList() {
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("type", "NameExpr");
    child.put("value", "ctx");

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "LambdaExpr");
    root.put("children", List.of(child));

    assertThat(json.write(root))
            .contains("\"type\":\"LambdaExpr\"")
            .contains("\"children\":[")
            .contains("\"value\":\"ctx\"");
  }

  @Test
  void serializesNullValues() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("value", null);

    assertThat(json.write(map)).isEqualTo("{\"value\":null}");
  }
}
