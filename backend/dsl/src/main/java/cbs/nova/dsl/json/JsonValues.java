package cbs.nova.dsl.json;

import cbs.nova.dsl.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class JsonValues {

  private JsonValues() {
  }

  /**
   * Returns a {@link JsonValue} representing a missing JSON value.
   */
  public static @NonNull JsonValue missing() {
    return new JacksonJsonValue(null);
  }

  public static @NonNull JsonValue of(@Nullable JsonNode node) {
    return new JacksonJsonValue(node);
  }

  /**
   * Converts a Java value into a {@link JsonValue} using the supplied mapper.
   *
   * <p>
   * Accepts:
   * <ul>
   * <li>{@code null} -> missing value</li>
   * <li>{@link JsonValue} -> returned as-is</li>
   * <li>{@link JsonNode} -> wrapped directly</li>
   * <li>{@link String} -> parsed as JSON</li>
   * <li>{@link java.util.Map} / {@link java.util.List} / records -> converted to JSON tree</li>
   * </ul>
   */
  public static @NonNull JsonValue of(@Nullable Object value, @NonNull ObjectMapper mapper) {
    if (value == null) {
      return missing();
    }
    if (value instanceof JsonValue jsonValue) {
      return jsonValue;
    }
    if (value instanceof JsonNode jsonNode) {
      return of(jsonNode);
    }
    if (value instanceof String string) {
      if (string.isBlank()) {
        return missing();
      }
      try {
        return of(mapper.readTree(string));
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
      }
    }
    return of(mapper.valueToTree(value));
  }
}
