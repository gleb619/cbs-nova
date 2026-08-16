package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.json.JsonValues;
import cbs.nova.starter.helpers.model.JsonExtractIn;
import cbs.nova.starter.helpers.model.JsonExtractOut;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

/**
 * Deprecated facade over the native {@link JsonValue} engine. Retained for backward compatibility
 * with existing DSL flows; new code should use context/expression JSON access directly.
 */
@Deprecated
@Helper(name = "jsonExtract")
public class JsonExtractHelper implements Executable<JsonExtractIn, JsonExtractOut> {

  private final @NonNull ObjectMapper mapper;

  /** Constructor for injecting a pre-configured {@link ObjectMapper}. */
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

    JsonValue terminal = navigate(value, input.path().split("\\."));
    if (!terminal.isPresent()) {
      return Result.success(new JsonExtractOut(null, false));
    }
    JsonNode raw = (JsonNode) terminal.raw();
    return Result.success(new JsonExtractOut(raw == null ? null : raw.asText(), true));
  }

  private @NonNull JsonValue navigate(@NonNull JsonValue root, @NonNull String[] segments) {
    JsonValue current = root;
    for (String segment : segments) {
      if (segment.isBlank()) {
        continue;
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
