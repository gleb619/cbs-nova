package cbs.nova.dsl.json;

import cbs.nova.dsl.JsonValue;
import io.avaje.jsonb.Jsonb;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AvajeJsonValue implements JsonValue {

  private static final Object MISSING = new Object();
  private static final Jsonb JSONB = Jsonb.builder().build();

  private final Object raw;

  private AvajeJsonValue() {
    this.raw = MISSING;
  }

  private AvajeJsonValue(@Nullable Object raw) {
    this.raw = raw;
  }

  public static @NonNull AvajeJsonValue missing() {
    return new AvajeJsonValue(MISSING);
  }

  public static @NonNull AvajeJsonValue of(@Nullable Object raw) {
    return new AvajeJsonValue(raw);
  }

  public static @NonNull AvajeJsonValue parse(@NonNull String json) {
    return new AvajeJsonValue(JSONB.type(Object.class).fromJson(json));
  }

  public @NonNull String toJson() {
    return JSONB.type(Object.class).toJson(raw());
  }

  @Override
  public @NonNull JsonValue get(@NonNull String field) {
    if (!(raw instanceof Map<?, ?> map)) {
      return missing();
    }
    return map.containsKey(field) ? of(map.get(field)) : missing();
  }

  @Override
  public @NonNull JsonValue get(int index) {
    if (!(raw instanceof List<?> list) || index < 0 || index >= list.size()) {
      return missing();
    }
    return of(list.get(index));
  }

  @Override
  public @Nullable String asString() {
    if (raw instanceof String string) {
      return string;
    }
    if (raw instanceof Number || raw instanceof Boolean) {
      return String.valueOf(raw);
    }
    return null;
  }

  @Override
  public @Nullable Integer asInt() {
    return raw instanceof Number number ? number.intValue() : null;
  }

  @Override
  public @Nullable Long asLong() {
    return raw instanceof Number number ? number.longValue() : null;
  }

  @Override
  public @Nullable Double asDouble() {
    return raw instanceof Number number ? number.doubleValue() : null;
  }

  @Override
  public @Nullable BigDecimal asDecimal() {
    if (raw instanceof Long longValue) {
      return BigDecimal.valueOf(longValue);
    }
    if (raw instanceof Integer intValue) {
      return BigDecimal.valueOf(intValue);
    }
    if (raw instanceof Double doubleValue) {
      return BigDecimal.valueOf(doubleValue);
    }
    return null;
  }

  @Override
  public @Nullable Boolean asBoolean() {
    return raw instanceof Boolean bool ? bool : null;
  }

  @Override
  public boolean isObject() {
    return raw instanceof Map<?, ?>;
  }

  @Override
  public boolean isArray() {
    return raw instanceof List<?>;
  }

  @Override
  public boolean isNull() {
    return raw == null;
  }

  @Override
  public boolean isPresent() {
    return raw != MISSING;
  }

  @Override
  public @NonNull List<JsonValue> asList() {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().<JsonValue>map(AvajeJsonValue::of).toList();
  }

  @Override
  public @NonNull Map<String, JsonValue> asMap() {
    if (raw instanceof Map<?, ?> map) {
      Map<String, JsonValue> result = new LinkedHashMap<>();
      map.forEach((key, val) -> result.put(String.valueOf(key), of(val)));
      return result;
    }

    return Map.of();
  }

  @Override
  public @Nullable Object raw() {
    return raw == MISSING ? null : raw;
  }

  @Override
  public @NonNull String toString() {
    return isPresent() ? toJson() : "null";
  }
}
