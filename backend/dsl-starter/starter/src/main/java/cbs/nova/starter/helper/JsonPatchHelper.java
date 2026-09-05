package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.JsonPatchIn;
import cbs.nova.starter.helper.model.JsonPatchOut;
import java.util.Locale;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applies or computes an RFC 7396 JSON Merge Patch.
 *
 * <p>
 * The helper supports two modes:
 * <ul>
 * <li>{@code "apply"}: merges {@code patch} into {@code source} per RFC 7396 and returns the
 * resulting object as a compact JSON string. {@code target} is ignored.</li>
 * <li>{@code "diff"}: produces an RFC 7396 merge patch document that, when applied to
 * {@code source}, yields {@code target}. {@code patch} is ignored.</li>
 * </ul>
 * Both {@code source} and either {@code patch} (apply) or {@code target} (diff) must be valid JSON
 * object strings; any deviation yields an {@link IllegalArgumentException}.
 */
@Helper(name = "jsonPatch")
public class JsonPatchHelper implements Executable<JsonPatchIn, JsonPatchOut> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public @NonNull Result<JsonPatchOut> execute(@NonNull Context<JsonPatchIn> ctx) {
    try {
      JsonPatchIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "apply" -> apply(input.source(), input.patch());
        case "diff" -> diff(input.source(), input.target());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "jsonPatch.mode must be 'apply' or 'diff', was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static @NonNull Result<JsonPatchOut> apply(String sourceJson, String patchJson) {
    if (sourceJson == null || sourceJson.isBlank()) {
      return Result.failure(new IllegalArgumentException("jsonPatch.source is required"));
    }
    if (patchJson == null || patchJson.isBlank()) {
      return Result.failure(new IllegalArgumentException("jsonPatch.patch is required"));
    }
    ObjectNode source = parseObject(sourceJson, "source");
    ObjectNode patch = parseObject(patchJson, "patch");
    ObjectNode result = applyMerge(source, patch);
    return Result.success(new JsonPatchOut(writeCompact(result)));
  }

  private static @NonNull Result<JsonPatchOut> diff(String sourceJson, String targetJson) {
    if (sourceJson == null || sourceJson.isBlank()) {
      return Result.failure(new IllegalArgumentException("jsonPatch.source is required"));
    }
    if (targetJson == null || targetJson.isBlank()) {
      return Result.failure(new IllegalArgumentException("jsonPatch.target is required"));
    }
    ObjectNode source = parseObject(sourceJson, "source");
    ObjectNode target = parseObject(targetJson, "target");
    ObjectNode result = computeDiff(source, target);
    return Result.success(new JsonPatchOut(writeCompact(result)));
  }

  private static @NonNull ObjectNode parseObject(String json, String field) {
    JsonNode node;
    try {
      node = MAPPER.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "jsonPatch: invalid JSON in " + field + ": " + e.getOriginalMessage(), e);
    }
    if (!node.isObject()) {
      throw new IllegalArgumentException(
              "jsonPatch requires object JSON, got: " + node.getNodeType().name());
    }
    return (ObjectNode) node;
  }

  private static @NonNull ObjectNode applyMerge(@NonNull ObjectNode source,
          @NonNull ObjectNode patch) {
    ObjectNode result = source.deepCopy();
    for (java.util.Map.Entry<String, JsonNode> entry : patch.properties()) {
      String name = entry.getKey();
      JsonNode value = entry.getValue();
      if (value.isNull()) {
        result.remove(name);
      } else {
        JsonNode existing = result.get(name);
        if (value.isObject() && existing != null && existing.isObject()) {
          result.set(name, applyMerge((ObjectNode) existing, (ObjectNode) value));
        } else {
          result.set(name, value.deepCopy());
        }
      }
    }
    return result;
  }

  private static @NonNull ObjectNode computeDiff(@NonNull ObjectNode source,
          @NonNull ObjectNode target) {
    ObjectNode diff = MAPPER.createObjectNode();
    java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
    source.propertyNames().forEach(keys::add);
    target.propertyNames().forEach(keys::add);
    for (String name : keys) {
      JsonNode s = source.get(name);
      JsonNode t = target.get(name);
      if (s == null) {
        diff.set(name, t.deepCopy());
      } else if (t == null) {
        diff.putNull(name);
      } else if (s.equals(t)) {
        continue;
      } else if (s.isObject() && t.isObject()) {
        ObjectNode sub = computeDiff((ObjectNode) s, (ObjectNode) t);
        if (!sub.isEmpty()) {
          diff.set(name, sub);
        }
      } else {
        diff.set(name, t.deepCopy());
      }
    }
    return diff;
  }

  private static @NonNull String writeCompact(@NonNull ObjectNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "jsonPatch: failed to serialize result: " + e.getOriginalMessage(), e);
    }
  }
}
