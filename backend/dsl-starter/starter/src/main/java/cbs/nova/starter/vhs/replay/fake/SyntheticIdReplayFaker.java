package cbs.nova.starter.vhs.replay.fake;

import cbs.nova.starter.vhs.TapeEvent;
import cbs.nova.starter.vhs.scrub.JsonPathNavigator;
import cbs.nova.starter.vhs.scrub.JsonPathNavigator.Segment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Replay-time faker that rewrites recorded tape events so that real identifiers, secrets, and
 * references are replaced with synthetic equivalents before the replay target executes the call.
 *
 * <p>
 * Per-session mapping table: the first time a real value appears anywhere in any faked path, it is
 * resolved to a synthetic value and stored. Subsequent encounters in the same session reuse the
 * stored mapping so that two calls referencing the same real id resolve to the same synthetic id.
 * The mapping is in-memory only; persisting it across sessions is a follow-up.
 *
 * <p>
 * <b>Determinism:</b> {@code synthetic_uuid} and {@code synthetic_hash} derivations are stable
 * across JVM restarts (they depend only on the value, not a session salt). {@code lookup} entries
 * are an explicit override; missing lookups fall back to {@code synthetic_uuid}.
 */
public final class SyntheticIdReplayFaker implements VhsReplayFaker {

  private final boolean enabled;
  private final List<FakeRule> rules;
  private final Map<String, String> lookupTable;
  private final String seed;

  /** Per-session mapping table: real value -> synthetic value for the lifetime of this faker. */
  private final Map<String, String> mapping = new ConcurrentHashMap<>();

  public SyntheticIdReplayFaker(
          boolean enabled,
          @Nullable List<FakeRule> rules,
          @Nullable Map<String, String> lookupTable,
          @Nullable String seed) {
    this.enabled = enabled;
    this.rules = rules == null ? List.of() : List.copyOf(rules);
    this.lookupTable = lookupTable == null ? Map.of() : Map.copyOf(lookupTable);
    this.seed = seed == null || seed.isBlank() ? "vhs-replay" : seed;
  }

  @Override
  public boolean enabled() {
    return enabled && !rules.isEmpty();
  }

  @Override
  public TapeEvent fake(TapeEvent event) {
    if (!enabled() || event.input() == null) {
      return event;
    }
    Object faked = applyAll(event.input());
    if (faked == event.input()) {
      return event;
    }
    return new TapeEvent(
            event.schemaVersion(),
            event.eventIndex(),
            event.eventType(),
            event.timestamp(),
            event.relativeMs(),
            event.callMetadata(),
            faked,
            event.output(),
            event.timing(),
            event.correlationId(),
            event.metadata());
  }

  private Object applyAll(Object root) {
    Object current = root;
    for (FakeRule rule : rules) {
      Object next = apply(current, JsonPathNavigator.parse(rule.path()), 0, rule);
      if (next != current) {
        current = next;
      }
    }
    return current;
  }

  private Object apply(Object value, List<Segment> segments, int pos, FakeRule rule) {
    if (value == null) {
      return null;
    }
    if (pos >= segments.size()) {
      // Path terminates here; this subtree IS the value to fake.
      return synthesize(value, rule);
    }
    Segment seg = segments.get(pos);
    boolean last = pos == segments.size() - 1;
    if (value instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) m;
      String key = seg.name();
      Object child = map.get(key);
      if (seg.wildcard()) {
        if (!(child instanceof List<?> list)) {
          return map;
        }
        List<Object> rewritten = new ArrayList<>(list.size());
        boolean changed = false;
        for (Object element : list) {
          Object after = apply(element, segments, pos + 1, rule);
          rewritten.add(after);
          changed |= after != element;
        }
        if (!changed) {
          return map;
        }
        Map<String, Object> copy = new HashMap<>(map);
        copy.put(key, rewritten);
        return copy;
      }
      if (last) {
        if (!map.containsKey(key)) {
          return map;
        }
        Object synthesized = synthesize(child, rule);
        if (synthesized == child) {
          return map;
        }
        Map<String, Object> copy = new HashMap<>(map);
        copy.put(key, synthesized);
        return copy;
      }
      if (!map.containsKey(key) || child == null) {
        return map;
      }
      Object scrubbed = apply(child, segments, pos + 1, rule);
      if (scrubbed == child) {
        return map;
      }
      Map<String, Object> copy = new HashMap<>(map);
      copy.put(key, scrubbed);
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> rewritten = new ArrayList<>(list.size());
      boolean changed = false;
      for (Object element : list) {
        Object after = apply(element, segments, pos, rule);
        rewritten.add(after);
        changed |= after != element;
      }
      return changed ? rewritten : list;
    }
    return value;
  }

  private Object synthesize(Object original, FakeRule rule) {
    if (original == null) {
      return null;
    }
    String real = String.valueOf(original);
    String mapped = mapping.get(real);
    if (mapped != null) {
      return mapped;
    }
    String resolved;
    if (rule.generate() == FakeRule.Generate.lookup) {
      String lookupHit = lookupTable.get(real);
      if (lookupHit != null) {
        resolved = lookupHit;
      } else {
        resolved = uuidForShape(real, rule.type());
      }
    } else if (rule.generate() == FakeRule.Generate.synthetic_hash) {
      resolved = hashForShape(real, original, rule.type());
    } else {
      resolved = uuidForShape(real, rule.type());
    }
    mapping.put(real, resolved);
    return resolved;
  }

  private String uuidForShape(String real, FakeRule.FakeType type) {
    String uuid = SyntheticValueGenerator.uuidFor(real);
    return switch (type) {
      case id, reference, generic -> uuid;
      case secret -> "sk_" + uuid.replace("-", "");
      case iban -> SyntheticValueGenerator.iban();
      case email -> SyntheticValueGenerator.email(real);
      case phone -> SyntheticValueGenerator.phone();
    };
  }

  private String hashForShape(String real, Object original, FakeRule.FakeType type) {
    int length = originalLength(original, type);
    String token = SyntheticValueGenerator.hashFor(original, seed, length);
    return switch (type) {
      case iban -> SyntheticValueGenerator.iban();
      case email -> SyntheticValueGenerator.email(real);
      case phone -> SyntheticValueGenerator.phone();
      default -> token;
    };
  }

  private static int originalLength(Object original, FakeRule.FakeType type) {
    if (type == FakeRule.FakeType.iban || type == FakeRule.FakeType.email
            || type == FakeRule.FakeType.phone) {
      return 0; // overridden in hashForShape via the domain helper
    }
    if (original instanceof CharSequence cs) {
      return Math.max(1, cs.length());
    }
    return 16;
  }
}
