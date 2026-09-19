package cbs.nova.starter.vhs.scrub;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JSON-path parser for the VHS scraper.
 *
 * <p>
 * Supports dot notation for object fields and {@code [*]} to fan out over every element of an
 * array. Examples: {@code $.token} resolves to the {@code token} field at the root;
 * {@code $.accounts[*].iban} fan outs over each element of {@code accounts} and selects
 * {@code iban}.
 *
 * <p>
 * The navigator only parses a path into {@link Segment}s; the actual traversal and mutation is
 * performed by {@link VhsScrubber} so that actions can clone only the maps/lists on the path that
 * actually change.
 */
public final class JsonPathNavigator {

  private JsonPathNavigator() {
  }

  /**
   * Parse a scrub path into an ordered list of segments.
   *
   * @param path
   *          the raw configured path, e.g. {@code $.accounts[*].iban}
   * @return the parsed segments; empty when the path carries no fields
   */
  public static List<Segment> parse(String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    String normalized = path.trim();
    if (normalized.startsWith("$")) {
      normalized = normalized.substring(1);
    }
    if (normalized.startsWith(".")) {
      normalized = normalized.substring(1);
    }
    if (normalized.isEmpty()) {
      return List.of();
    }

    String[] parts = normalized.split("\\.");
    List<Segment> segments = new ArrayList<>(parts.length);
    for (String part : parts) {
      if (part.endsWith("[]")) {
        // Lenient: treat bare `[]` like `[*]`.
        segments.add(Segment.wildcard(part.substring(0, part.length() - 2)));
      } else if (part.endsWith("[*]")) {
        segments.add(Segment.wildcard(part.substring(0, part.length() - 3)));
      } else {
        segments.add(Segment.field(part));
      }
    }
    return List.copyOf(segments);
  }

  /**
   * One step of a parsed scrub path.
   *
   * @param name
   *          the object field name this segment selects
   * @param wildcard
   *          whether this segment should fan out over the array elements stored under {@code name}
   */
  public record Segment(String name, boolean wildcard) {

    private static Segment field(String name) {
      return new Segment(name, false);
    }

    private static Segment wildcard(String name) {
      return new Segment(name, true);
    }

    public Segment {
      name = name == null ? "" : name;
    }
  }
}
