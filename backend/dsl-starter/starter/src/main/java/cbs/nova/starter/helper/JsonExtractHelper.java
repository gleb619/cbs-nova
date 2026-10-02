package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.json.JsonValues;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.JsonExtractIn;
import cbs.nova.starter.helper.model.JsonExtractOut;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Deprecated
@SpringHelper(name = "jsonExtract")
public class JsonExtractHelper implements Executable<JsonExtractIn, JsonExtractOut> {

  private final @NonNull ObjectMapper mapper;

  public JsonExtractHelper(@NonNull ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public @NonNull Result<JsonExtractOut> execute(@NonNull Context<JsonExtractIn> ctx) {
    JsonExtractIn input = ctx.body();
    if (input.json() == null || input.json().isBlank()) {
      return Result.success(new JsonExtractOut(null, false));
    }
    if (input.path() == null || input.path().isBlank()) {
      return Result.success(new JsonExtractOut(null, false));
    }

    JsonValue value;
    try {
      value = JsonValues.of(input.json(), mapper);
    } catch (IllegalArgumentException e) {
      return Result.failure(new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e));
    }

    String[] segments = tokenize(input.path());
    if (segments == null || segments.length == 0) {
      return Result.success(new JsonExtractOut(null, false));
    }
    JsonValue terminal = navigate(value, segments);
    if (!terminal.isPresent()) {
      return Result.success(new JsonExtractOut(null, false));
    }
    JsonNode raw = (JsonNode) terminal.raw();
    return Result.success(new JsonExtractOut(raw == null ? null : raw.asText(), true));
  }

  /**
   * Normalizes a JSONPath-style expression into the flat segment list consumed by
   * {@link #navigate}. Accepts:
   *
   * <ul>
   * <li>a leading {@code $} (with an optional following {@code .}) — stripped;</li>
   * <li>plain dotted keys, e.g. {@code a.b.c};</li>
   * <li>numeric array indices in brackets, e.g. {@code items[0].id}; the bracket form is expanded
   * into {@code items}, {@code 3} segments.</li>
   * </ul>
   *
   * <p>
   * Blank or {@code $}-only paths resolve to the document root and yield {@code present = false}
   * (matching the pre-existing blank-path contract). Malformed bracket expressions such as
   * {@code items[x]} or {@code items[} also resolve to {@code present = false} without throwing.
   */
  @NonNull
  static String @Nullable [] tokenize(@NonNull String path) {
    String p = path;
    // `$` is the JSONPath root marker only when it stands alone or is followed by `.` / `[`;
    // keys that merely start with `$` (`$ref`, `$schema`, `$id`) stay literal as they always did.
    if (p.startsWith("$") && (p.length() == 1 || p.charAt(1) == '.' || p.charAt(1) == '[')) {
      p = p.substring(1);
      if (p.startsWith(".")) {
        p = p.substring(1);
      }
    }
    if (p.isEmpty()) {
      return new String[0];
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < p.length(); i++) {
      char c = p.charAt(i);
      if (c == '.') {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
      } else if (c == '[') {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        int close = p.indexOf(']', i + 1);
        if (close < 0) {
          return null;
        }
        String inside = p.substring(i + 1, close);
        if (inside.isEmpty() || !inside.chars().allMatch(Character::isDigit)) {
          return null;
        }
        out.add(inside);
        i = close;
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out.toArray(new String[0]);
  }

  private @NonNull JsonValue navigate(@NonNull JsonValue root, @NonNull String[] segments) {
    JsonValue current = root;
    for (String segment : segments) {
      if (segment.isBlank()) {
        return JsonValues.missing();
      }
      if (current.isArray()) {
        int index;
        try {
          index = Integer.parseInt(segment);
        } catch (NumberFormatException e) {
          return JsonValues.missing();
        }
        current = current.get(index);
      } else {
        current = current.get(segment);
      }
      if (!current.isPresent()) {
        break;
      }
    }
    return current;
  }
}
