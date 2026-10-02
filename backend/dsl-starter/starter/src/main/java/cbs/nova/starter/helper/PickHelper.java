package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.PickIn;
import cbs.nova.starter.helper.model.PickOut;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Projects a {@code Map} down to ({@code pick} mode) or minus ({@code omit} mode) a list of keys —
 * useful for shaping outbound {@code httpCall} / webhook payloads.
 *
 * <p>
 * Two modes (case-insensitive):
 * <ul>
 * <li>{@code "pick"} (default when {@code mode} is {@code null}/blank): keeps only the listed keys.
 * The result contains each listed key that is present in {@code source}, in {@code keys} order;
 * keys absent from {@code source} are silently skipped (not null-filled). A key that is present
 * with a {@code null} value is kept — {@code null} is a value, not absence.</li>
 * <li>{@code "omit"}: returns a copy of {@code source} (insertion order preserved) with every
 * listed key removed, including present-but-null ones.</li>
 * </ul>
 *
 * <p>
 * Dotted keys ({@code "a.b"}) are split into nested {@code Map} segments: {@code pick "a.b"}
 * produces {@code {a: {b: <value>}}}, multiple paths sharing a prefix merge into one nested map
 * ({@code "a.b"}, {@code "a.c"} → {@code {a: {b, c}}}), absent paths are silently skipped,
 * present-but-null leaves are kept, and a non-{@code Map} intermediate is treated as absent (no
 * error). A flat key that literally contains {@code "."} takes precedence over a path split — so
 * existing flat callers are unaffected. Nested values are copied by reference; only the {@code Map}
 * segments along a pick/omit path are deep-copied on write (sibling branches are shared by
 * reference). Lists are not traversed — {@code "a[0]"} / {@code "a[*]"} are out of scope for this
 * helper. The input {@code source} map is never mutated.
 */
@Helper(name = "pick")
public class PickHelper implements Executable<PickIn, PickOut> {

  @Override
  public @NonNull Result<PickOut> execute(@NonNull Context<PickIn> ctx) {
    try {
      PickIn input = ctx.body();
      Map<String, Object> source = input.source();
      if (source == null) {
        return Result.failure(new IllegalArgumentException("pick.source is required"));
      }
      List<String> keys = input.keys();
      if (keys == null || keys.isEmpty()) {
        return Result.failure(new IllegalArgumentException("pick.keys must be non-empty"));
      }
      String mode = (input.mode() == null || input.mode().isBlank())
              ? "pick"
              : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "pick" -> Result.success(new PickOut(pick(source, keys)));
        case "omit" -> Result.success(new PickOut(omit(source, keys)));
        default -> Result.failure(new IllegalArgumentException(
                "pick.mode must be one of pick, omit, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  // --- mode: pick ------------------------------------------------------

  private static Map<String, Object> pick(Map<String, Object> source, List<String> keys) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : keys) {
      // Flat match wins: a literal key containing '.' matches first so existing
      // flat callers (e.g. {"a.b": 1}) are unaffected by dotted-path support.
      if (source.containsKey(key)) {
        result.put(key, source.get(key));
        continue;
      }
      int dot = key.indexOf('.');
      if (dot < 0) {
        continue; // simple flat key absent — silently skipped (not null-filled)
      }
      String head = key.substring(0, dot);
      Object headValue = source.get(head);
      if (!(headValue instanceof Map<?, ?>)) {
        continue; // absent or non-map (String/List) intermediate — treat as absent
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> headMap = (Map<String, Object>) headValue;
      List<String> segments = splitOnDot(key, dot + 1);
      Object leaf = resolveLeaf(headMap, segments);
      if (leaf == ABSENT_LEAF) {
        continue; // path absent at some level — silently skipped
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> existing = (Map<String, Object>) result.get(head);
      Map<String, Object> subtree = (existing != null) ? existing : new LinkedHashMap<>();
      insertLeaf(subtree, segments, leaf);
      result.put(head, subtree);
    }
    return result;
  }

  // --- mode: omit ------------------------------------------------------

  private static Map<String, Object> omit(Map<String, Object> source, List<String> keys) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    for (String key : keys) {
      // Flat match wins: a literal key containing '.' matches first so existing
      // flat callers (e.g. {"a.b": 1}) are unaffected by dotted-path support.
      if (result.containsKey(key)) {
        result.remove(key);
        continue;
      }
      int dot = key.indexOf('.');
      if (dot < 0) {
        result.remove(key);
        continue;
      }
      String head = key.substring(0, dot);
      Object headValue = result.get(head);
      if (!(headValue instanceof Map<?, ?>)) {
        continue; // absent or non-map — no-op
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> original = (Map<String, Object>) headValue;
      List<String> segments = splitOnDot(key, dot + 1);
      // Copy-on-write: copy maps along the path; sibling branches are shared by reference
      // (same shallow-copy contract as the flat omit path). Returns null if path absent.
      Map<String, Object> copy = copyPathRemoveLeaf(original, segments);
      if (copy == null) {
        continue; // absent in source — no-op
      }
      result.put(head, copy);
    }
    return result;
  }

  // --- dotted-path helpers ---------------------------------------------

  /** Sentinel distinguishing "absent at this path" from a present-but-null leaf. */
  private static final Object ABSENT_LEAF = new Object();

  /** Split {@code key} into path segments starting at {@code fromIndex}. */
  private static List<String> splitOnDot(String key, int fromIndex) {
    List<String> out = new ArrayList<>();
    int start = fromIndex;
    while (start < key.length()) {
      int dot = key.indexOf('.', start);
      int end = (dot < 0) ? key.length() : dot;
      out.add(key.substring(start, end));
      if (dot < 0) {
        break;
      }
      start = end + 1;
    }
    return out;
  }

  /**
   * Walk {@code map} along {@code segments}. Returns the leaf value (which may be {@code null} when
   * present-but-null) or {@link #ABSENT_LEAF} if any segment is missing or an intermediate is not a
   * {@code Map}.
   */
  private static Object resolveLeaf(Map<String, Object> map, List<String> segments) {
    Object current = map;
    for (int i = 0; i < segments.size(); i++) {
      if (!(current instanceof Map<?, ?>)) {
        return ABSENT_LEAF;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> cm = (Map<String, Object>) current;
      String segment = segments.get(i);
      if (!cm.containsKey(segment)) {
        return ABSENT_LEAF;
      }
      current = cm.get(segment);
    }
    return current;
  }

  /** Insert {@code leaf} into {@code subtree} at the path defined by {@code segments}. */
  private static void insertLeaf(
          Map<String, Object> subtree, List<String> segments, Object leaf) {
    Map<String, Object> current = subtree;
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      boolean last = (i == segments.size() - 1);
      if (last) {
        current.put(segment, leaf);
        return;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> next = (Map<String, Object>) current.get(segment);
      if (next == null) {
        next = new LinkedHashMap<>();
        current.put(segment, next);
      }
      current = next;
    }
  }

  /**
   * Copy-on-write for {@code omit}: walk {@code segments} through {@code map}, copying only the
   * maps on the path; siblings are shared by reference. Removes the leaf along the way. Returns
   * {@code null} if any segment is missing or an intermediate is not a {@code Map}.
   */
  private static Map<String, Object> copyPathRemoveLeaf(
          Map<String, Object> map, List<String> segments) {
    if (resolveLeaf(map, segments) == ABSENT_LEAF) {
      return null;
    }
    // Path is present; build a copy from the top down.
    Map<String, Object> copy = new LinkedHashMap<>(map);
    Map<String, Object> currentSrc = map;
    Map<String, Object> currentDst = copy;
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      boolean last = (i == segments.size() - 1);
      if (last) {
        currentDst.remove(segment);
        break;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> childSrc = (Map<String, Object>) currentSrc.get(segment);
      Map<String, Object> childDst = new LinkedHashMap<>(childSrc);
      currentDst.put(segment, childDst);
      currentSrc = childSrc;
      currentDst = childDst;
    }
    return copy;
  }
}
