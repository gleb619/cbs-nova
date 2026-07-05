package cbs.nova.dsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MapInput {

  private MapInput() {
  }

  public static Map<String, Object> of(Object... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException(
              "MapInput.of() requires an even number of arguments, got " + keyValuePairs.length);
    }
    if (keyValuePairs.length == 0) {
      return Collections.emptyMap();
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
    return Collections.unmodifiableMap(map);
  }
}
