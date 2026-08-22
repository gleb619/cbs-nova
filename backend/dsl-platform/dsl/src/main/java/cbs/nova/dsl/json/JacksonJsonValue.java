package cbs.nova.dsl.json;

import cbs.nova.dsl.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JacksonJsonValue implements JsonValue {

  private final @NonNull JsonNode node;

  public JacksonJsonValue(@Nullable JsonNode node) {
    this.node = node == null ? MissingNode.getInstance() : node;
  }

  @Override
  public @NonNull JsonValue get(@NonNull String field) {
    if (!node.isObject()) {
      return new JacksonJsonValue(MissingNode.getInstance());
    }
    return new JacksonJsonValue(node.get(field));
  }

  @Override
  public @NonNull JsonValue get(int index) {
    if (!node.isArray()) {
      return new JacksonJsonValue(MissingNode.getInstance());
    }
    return new JacksonJsonValue(node.get(index));
  }

  @Override
  public @Nullable String asString() {
    if (!isPresent() || isNull() || isObject() || isArray()) {
      return null;
    }
    return node.asText();
  }

  @Override
  public @Nullable Integer asInt() {
    if (!node.isNumber()) {
      return null;
    }
    return node.asInt();
  }

  @Override
  public @Nullable Long asLong() {
    if (!node.isNumber()) {
      return null;
    }
    return node.asLong();
  }

  @Override
  public @Nullable Double asDouble() {
    if (!node.isNumber()) {
      return null;
    }
    return node.asDouble();
  }

  @Override
  public @Nullable BigDecimal asDecimal() {
    if (!node.isNumber()) {
      return null;
    }
    return node.decimalValue();
  }

  @Override
  public @Nullable Boolean asBoolean() {
    if (!node.isBoolean()) {
      return null;
    }
    return node.asBoolean();
  }

  @Override
  public boolean isObject() {
    return node.isObject();
  }

  @Override
  public boolean isArray() {
    return node.isArray();
  }

  @Override
  public boolean isNull() {
    return node.isNull();
  }

  @Override
  public boolean isPresent() {
    return !node.isMissingNode();
  }

  @Override
  public @NonNull List<JsonValue> asList() {
    if (!node.isArray()) {
      return List.of();
    }
    ArrayNode array = (ArrayNode) node;
    List<JsonValue> list = new ArrayList<>(array.size());
    for (JsonNode element : array) {
      list.add(new JacksonJsonValue(element));
    }
    return List.copyOf(list);
  }

  @Override
  public @NonNull Map<String, JsonValue> asMap() {
    if (!node.isObject()) {
      return Map.of();
    }
    Map<String, JsonValue> map = new LinkedHashMap<>();
    node.properties().forEach(
            entry -> map.put(entry.getKey(), new JacksonJsonValue(entry.getValue())));
    return Collections.unmodifiableMap(map);
  }

  @Override
  public @Nullable Object raw() {
    return node;
  }

  @Override
  public @NonNull String toString() {
    return node.toString();
  }
}
