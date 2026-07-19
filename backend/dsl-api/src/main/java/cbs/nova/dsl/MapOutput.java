package cbs.nova.dsl;

import io.avaje.jsonb.Json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable wrapper for a parameter-based output map. Returned by DSL processes, transactions, and
 * functions that declare {@code .parameters(...)} instead of typed {@code .input()/.output()}.
 */
@Json
public record MapOutput(Map<String, Object> values) {

  public MapOutput {
    values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /**
   * Creates a {@link MapOutput} from varargs key-value pairs.
   */
  public static MapOutput of(Object... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException(
              "MapOutput.of() requires an even number of arguments, got " + keyValuePairs.length);
    }
    if (keyValuePairs.length == 0) {
      return new MapOutput(Map.of());
    }
    var map = new LinkedHashMap<String, Object>(keyValuePairs.length / 2);
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      if (!(keyValuePairs[i] instanceof String key)) {
        throw new IllegalArgumentException(
                "MapOutput.of() keys must be Strings, got: "
                        + keyValuePairs[i].getClass().getName()
                        + " at index "
                        + i);
      }
      map.put(key, keyValuePairs[i + 1]);
    }
    return new MapOutput(map);
  }

  /**
   * Creates a {@link MapOutput} from an existing map.
   */
  public static MapOutput fromMap(Map<String, Object> map) {
    return map == null ? new MapOutput(Map.of()) : new MapOutput(map);
  }

  /**
   * Returns a mutable copy of the underlying values.
   */
  public Map<String, Object> asMap() {
    return new LinkedHashMap<>(values);
  }
}
