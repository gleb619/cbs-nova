package cbs.nova.starter.vhs.scrub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable record-time redactor for VHS tape events.
 *
 * <p>
 * Runs in the recorder hot path after event translation and before the sink append. Given the
 * {@code input}/{@code output}/{@code metadata} tree of a {@code TapeEvent}, each configured
 * {@link ScrubRule} is applied in order.
 *
 * <p>
 * <b>No-op guarantee:</b> when {@code enabled=false} or {@code rules} is empty, {@link #scrub}
 * returns the original object reference without copying, so the recorder path has zero measurable
 * overhead.
 *
 * <p>
 * Redaction is structural: it clones only the maps/lists on the path that actually change and
 * leaves the caller's original tree untouched.
 */
@Slf4j
public final class VhsScrubber {

  /**
   * Default value used by the {@code mask} action and the {@code cbs.vhs.scrub.mask-value} default.
   */
  public static final String DEFAULT_MASK = "***REDACTED***";

  private final boolean enabled;
  private final String seed;
  private final String maskValue;
  private final List<ScrubRule> rules;
  private final VhsFaker faker = new VhsFaker();

  private final Set<String> warnedPaths = new HashSet<>();

  /**
   * @param enabled
   *          master switch; when {@code false} the scrubber is a no-op pass-through
   * @param rules
   *          ordered scrub rules
   * @param maskValue
   *          value used by the {@code mask} action; {@code null}/{@code blank} falls back to
   *          {@link #DEFAULT_MASK}
   * @param seed
   *          seed for the {@code fake} action; when {@code null} a random per-instance seed is
   *          generated
   */
  public VhsScrubber(
          boolean enabled,
          @Nullable List<ScrubRule> rules,
          @Nullable String maskValue,
          @Nullable String seed) {
    this.enabled = enabled;
    this.rules = rules == null ? List.of() : List.copyOf(rules);
    this.maskValue = maskValue == null || maskValue.isBlank() ? DEFAULT_MASK : maskValue;
    this.seed = seed != null ? seed : "vhs-" + UUID.randomUUID();
  }

  public static VhsScrubber disabled() {
    return new VhsScrubber(false, List.of(), DEFAULT_MASK, null);
  }

  public boolean enabled() {
    return enabled;
  }

  /**
   * Apply every configured rule to {@code value}.
   *
   * <p>
   * When scrubbing is disabled or no rules are configured this returns {@code value} by reference.
   * The {@code section} label ({@code input}/{@code output}/{@code metadata}) is only used for
   * warning messages.
   */
  public Object scrub(@NonNull String section, @Nullable Object value) {
    if (!enabled || rules.isEmpty() || value == null) {
      return value;
    }
    Object result = value;
    for (ScrubRule rule : rules) {
      result = apply(section, result, JsonPathNavigator.parse(rule.path()), 0, rule);
    }
    return result;
  }

  private Object apply(String section, Object value, List<JsonPathNavigator.Segment> segments,
          int pos, ScrubRule rule) {
    if (value == null) {
      return null;
    }
    if (pos >= segments.size()) {
      // The whole subtree was selected (e.g. `$.tags[*]` terminal): apply the action to the value.
      return transform(section, value, rule);
    }

    JsonPathNavigator.Segment seg = segments.get(pos);
    boolean last = pos == segments.size() - 1;

    if (value instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) m;
      String key = seg.name();
      Object child = map.get(key);

      if (seg.wildcard()) {
        if (!(child instanceof List<?> list)) {
          if (last) {
            warnMissing(section, rule);
          }
          return map;
        }
        List<Object> scrubbedList = fanOut(section, list, segments, pos + 1, rule);
        if (scrubbedList == child) {
          return map;
        }
        Map<String, Object> copy = new HashMap<>(map);
        copy.put(key, scrubbedList);
        return copy;
      }

      if (last) {
        return applyAtField(section, map, key, rule);
      }
      if (!map.containsKey(key) || child == null) {
        // Missing intermediate path: skip silently.
        return map;
      }
      Object scrubbed = apply(section, child, segments, pos + 1, rule);
      if (scrubbed == child) {
        return map;
      }
      Map<String, Object> copy = new HashMap<>(map);
      copy.put(key, scrubbed);
      return copy;
    }

    if (value instanceof List<?> list) {
      // A list reached by a non-wildcard segment fans out over its elements.
      List<Object> scrubbed = fanOut(section, list, segments, pos, rule);
      return scrubbed == list ? list : scrubbed;
    }

    return value;
  }

  private List<Object> fanOut(String section, List<?> list,
          List<JsonPathNavigator.Segment> segments, int pos, ScrubRule rule) {
    List<Object> result = new ArrayList<>(list.size());
    boolean changed = false;
    for (Object element : list) {
      Object scrubbed = apply(section, element, segments, pos, rule);
      result.add(scrubbed);
      changed |= scrubbed != element;
    }
    @SuppressWarnings("unchecked")
    List<Object> original = (List<Object>) list;
    return changed ? result : original;
  }

  private Map<String, Object> applyAtField(String section, Map<String, Object> map, String key,
          ScrubRule rule) {
    if (!map.containsKey(key)) {
      warnMissing(section, rule);
      return map;
    }
    Object original = map.get(key);
    Map<String, Object> copy = new HashMap<>(map);
    switch (rule.action()) {
      case remove -> copy.remove(key);
      case mask -> copy.put(key, maskValue);
      case fake -> copy.put(key, faker.fake(key, original, seed));
    }
    return copy;
  }

  private Object transform(String section, Object value, ScrubRule rule) {
    return switch (rule.action()) {
      case mask -> maskValue;
      case fake -> faker.fake(section, value, seed);
      case remove -> value;
    };
  }

  private void warnMissing(String section, ScrubRule rule) {
    if (warnedPaths.add(section + ":" + rule.path())) {
      log.warn(
              "VHS scrub path '{}' not found in '{}'; skipping rule (action={})",
              rule.path(), section, rule.action());
    }
  }
}
