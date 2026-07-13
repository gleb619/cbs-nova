package cbs.nova.dsl.codegen;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Tiny JSON serializer used by the code generator. Supports the value types produced by the
 * AST-to-tree converter: null, booleans, numbers, strings, lists and string-keyed maps.
 */
final class Json {

  private Json() {
  }

  static @NonNull String write(@NonNull Object value) {
    var sb = new StringBuilder();
    write(value, sb);
    return sb.toString();
  }

  private static void write(Object value, StringBuilder sb) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof Boolean || value instanceof Number) {
      sb.append(value);
    } else if (value instanceof String s) {
      escape(s, sb);
    } else if (value instanceof List<?> list) {
      sb.append('[');
      boolean first = true;
      for (Object item : list) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        write(item, sb);
      }
      sb.append(']');
    } else if (value instanceof Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        escape(String.valueOf(entry.getKey()), sb);
        sb.append(':');
        write(entry.getValue(), sb);
      }
      sb.append('}');
    } else {
      escape(value.toString(), sb);
    }
  }

  private static void escape(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
