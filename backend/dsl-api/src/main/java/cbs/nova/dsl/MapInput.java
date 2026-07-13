package cbs.nova.dsl;

import io.avaje.jsonb.Json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable wrapper for a parameter map. Used as the typed input/output for parameter-based
 * DSL processes, transactions, and functions, and as a convenient factory for map literals
 * in DSL authoring.
 */
@Json
public record MapInput(Map<String, Object> values) {

  public MapInput {
    values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /**
   * Creates a {@link MapInput} from varargs key-value pairs.
   */
  public static MapInput of(Object... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException(
              "MapInput.of() requires an even number of arguments, got " + keyValuePairs.length);
    }
    if (keyValuePairs.length == 0) {
      return new MapInput(Map.of());
    }
    var map = new LinkedHashMap<String, Object>(keyValuePairs.length / 2);
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      if (!(keyValuePairs[i] instanceof String key)) {
        throw new IllegalArgumentException(
                "MapInput.of() keys must be Strings, got: "
                        + keyValuePairs[i].getClass().getName()
                        + " at index "
                        + i);
      }
      map.put(key, keyValuePairs[i + 1]);
    }
    return new MapInput(map);
  }

  /**
   * Creates a {@link MapInput} from an existing map.
   */
  public static MapInput fromMap(Map<String, Object> map) {
    return map == null ? new MapInput(Map.of()) : new MapInput(map);
  }

  /**
   * Returns a mutable copy of the underlying values.
   */
  public Map<String, Object> asMap() {
    return new LinkedHashMap<>(values);
  }
}
