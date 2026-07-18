package cbs.nova.dsl.codegen.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public final class Json {

  public @NonNull String write(@NonNull Object value) {
    return switch (value) {
      case Map<?, ?> map -> wrap(map).toString();
      case List<?> list -> wrap(list).toString();
      case String s -> JSONObject.quote(s);
      case Boolean _,Number _ -> value.toString();
      default -> JSONObject.quote(value.toString());
    };
  }

  private static @NonNull Object wrap(@NonNull Object value) {
    if (value instanceof Map<?, ?> map) {
      JSONObject obj = new JSONObject();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object v = entry.getValue();
        obj.put(String.valueOf(entry.getKey()), v == null ? JSONObject.NULL : wrap(v));
      }
      return obj;
    }
    if (value instanceof List<?> list) {
      JSONArray arr = new JSONArray();
      for (Object item : list) {
        arr.put(item == null ? JSONObject.NULL : wrap(item));
      }
      return arr;
    }
    return value;
  }
}
