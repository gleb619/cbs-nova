package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.JsonDiffIn;
import cbs.nova.starter.helper.model.JsonDiffOut;
import cbs.nova.starter.helper.model.JsonDiffOut.Change;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Computes a flat, JSONPointer-style diff between two JSON documents.
 *
 * <p>
 * Walks {@code left} and {@code right} in parallel and emits one entry per differing leaf or
 * per-array-index mismatch: {@code {path, op, left?, right?}} where {@code op} is {@code "added"},
 * {@code "removed"}, or {@code "changed"}. The output is a flat list
 * ({@link JsonDiffOut#changes()}) plus a boolean {@link JsonDiffOut#identical()}.
 *
 * <p>
 * Path notation: a leading {@code $}, object keys joined with {@code .}, and array indices in
 * {@code [n]} segments — e.g. {@code $.a.b[0].c}. This matches the {@code schemaValidate}
 * convention.
 *
 * <p>
 * Null-vs-missing distinction: {@code null} present on one side and absent on the other are
 * different. When only the type differs (e.g. object vs scalar, array vs object) the entry is
 * {@code op = "changed"} with both sides populated. Arrays are diffed by index position; differing
 * lengths produce {@code added}/{@code removed} entries for the longer side's trailing indices.
 *
 * <p>
 * Both inputs invalid JSON yields {@link Result#failure} with a message naming both sides. A
 * {@code null} or blank input also fails with a clear message — empty-string JSON is treated as a
 * missing value rather than an empty document.
 */
@Helper(name = "jsonDiff")
public class JsonDiffHelper implements Executable<JsonDiffIn, JsonDiffOut> {

  static final String OP_ADDED = "added";
  static final String OP_REMOVED = "removed";
  static final String OP_CHANGED = "changed";

  private final ObjectMapper mapper;

  public JsonDiffHelper() {
    this(new ObjectMapper());
  }

  public JsonDiffHelper(@NonNull ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public @NonNull Result<JsonDiffOut> execute(@NonNull Context<JsonDiffIn> ctx) {
    try {
      JsonDiffIn input = ctx.body();
      if (input == null || input.left() == null || input.left().isBlank()) {
        return Result.failure(new IllegalArgumentException("jsonDiff.left is required"));
      }
      if (input.right() == null || input.right().isBlank()) {
        return Result.failure(new IllegalArgumentException("jsonDiff.right is required"));
      }

      JsonNode left;
      JsonNode right;
      try {
        left = mapper.readTree(input.left());
      } catch (JacksonException e) {
        return Result.failure(
                new IllegalArgumentException(
                        "jsonDiff: invalid JSON in left: " + e.getOriginalMessage(), e));
      }
      try {
        right = mapper.readTree(input.right());
      } catch (JacksonException e) {
        return Result.failure(
                new IllegalArgumentException(
                        "jsonDiff: invalid JSON in right: " + e.getOriginalMessage(), e));
      }

      List<Change> changes = new ArrayList<>();
      diff(left, right, "$", changes);
      return Result.success(new JsonDiffOut(changes, changes.isEmpty()));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private void diff(JsonNode left, JsonNode right, String path, List<Change> sink) {
    boolean leftMissing = isMissing(left);
    boolean rightMissing = isMissing(right);

    if (leftMissing && rightMissing) {
      return;
    }
    if (leftMissing) {
      sink.add(new Change(path, OP_ADDED, null, encode(right)));
      return;
    }
    if (rightMissing) {
      sink.add(new Change(path, OP_REMOVED, encode(left), null));
      return;
    }

    if (left.getNodeType() != right.getNodeType()) {
      sink.add(new Change(path, OP_CHANGED, encode(left), encode(right)));
      return;
    }

    if (left.isObject() && right.isObject()) {
      diffObjects(left, right, path, sink);
      return;
    }
    if (left.isArray() && right.isArray()) {
      diffArrays(left, right, path, sink);
      return;
    }
    if (!left.equals(right)) {
      sink.add(new Change(path, OP_CHANGED, encode(left), encode(right)));
    }
  }

  private void diffObjects(JsonNode left, JsonNode right, String parent, List<Change> sink) {
    java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
    java.util.LinkedHashMap<String, JsonNode> leftMap = new java.util.LinkedHashMap<>();
    java.util.LinkedHashMap<String, JsonNode> rightMap = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : left.properties()) {
      keys.add(entry.getKey());
      leftMap.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, JsonNode> entry : right.properties()) {
      keys.add(entry.getKey());
      rightMap.put(entry.getKey(), entry.getValue());
    }
    for (String key : keys) {
      String child = parent + "." + key;
      diff(leftMap.get(key), rightMap.get(key), child, sink);
    }
  }

  private void diffArrays(JsonNode left, JsonNode right, String parent, List<Change> sink) {
    int leftSize = left.size();
    int rightSize = right.size();
    int common = Math.min(leftSize, rightSize);
    for (int i = 0; i < common; i++) {
      diff(left.get(i), right.get(i), parent + "[" + i + "]", sink);
    }
    for (int i = common; i < leftSize; i++) {
      sink.add(new Change(parent + "[" + i + "]", OP_REMOVED, encode(left.get(i)), null));
    }
    for (int i = common; i < rightSize; i++) {
      sink.add(new Change(parent + "[" + i + "]", OP_ADDED, null, encode(right.get(i))));
    }
  }

  private static boolean isMissing(JsonNode node) {
    return node == null || node.isMissingNode();
  }

  private String encode(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    try {
      return mapper.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "jsonDiff: failed to encode diff value: " + e.getOriginalMessage(), e);
    }
  }
}
