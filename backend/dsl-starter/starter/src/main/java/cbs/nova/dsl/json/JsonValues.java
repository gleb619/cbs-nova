package cbs.nova.dsl.json;

import cbs.nova.dsl.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Deprecated
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
