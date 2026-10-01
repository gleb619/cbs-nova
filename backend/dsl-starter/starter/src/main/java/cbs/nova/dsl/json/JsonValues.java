package cbs.nova.dsl.json;

import cbs.nova.dsl.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Factory for creating {@link JsonValue} instances backed by Jackson {@link JsonNode}.
 *
 * <p>
 * Starter-tier counterpart to the platform's Avaje-backed JSON value type. Use this when the active
 * {@code ObjectMapper} is Jackson (the default in {@code dsl-starter}); prefer the Avaje-backed
 * factory in modules that wire {@code AvajeJsonValue} instead.
 */
public final class JsonValues {

  private JsonValues() {
  }

  public static @NonNull JsonValue missing() {
    return new JacksonJsonValue(null);
  }

  public static @NonNull JsonValue of(@Nullable JsonNode node) {
    return new JacksonJsonValue(node);
  }

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
      } catch (JacksonException e) {
        throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
      }
    }
    return of(mapper.valueToTree(value));
  }
}
