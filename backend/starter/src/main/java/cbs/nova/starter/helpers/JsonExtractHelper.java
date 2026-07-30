package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.JsonExtractIn;
import cbs.nova.starter.helpers.model.JsonExtractOut;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

// TODO: for later releases, we need a better json integration, dsl must be a json native. So it
// must be not a single helper but a core feature, that used in `context`, or be a part of
// functions. Promoted to a separate kanban task: "Make JSON a first-class DSL citizen (native
// JSON path/function support)".
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

    JsonNode root;
    try {
      root = mapper.readTree(input.json());
    } catch (JsonProcessingException e) {
      return Result.failure(new IllegalArgumentException("Invalid JSON: " + e.getMessage()));
    }

    JsonNode node = navigate(root, input.path().split("\\."));
    if (node == null || node.isMissingNode()) {
      return Result.success(new JsonExtractOut(null, false));
    }
    return Result.success(new JsonExtractOut(node.asText(), true));
  }

  private @NonNull JsonNode navigate(@NonNull JsonNode root, @NonNull String[] segments) {
    JsonNode current = root;
    for (String segment : segments) {
      if (segment.isBlank()) {
        return current;
      }
      if (current.isArray()) {
        int index;
        try {
          index = Integer.parseInt(segment);
        } catch (NumberFormatException e) {
          return current.get(segment);
        }
        current = current.get(index);
      } else {
        current = current.get(segment);
      }
      if (current == null) {
        break;
      }
    }
    return current != null ? current : mapper.missingNode();
  }
}
