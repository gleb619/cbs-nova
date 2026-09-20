package cbs.nova.dsl.explain;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Minimal YAML-frontmatter reader for explain markdown files. Supports a strict subset:
 *
 * <ul>
 * <li>First non-empty line must be {@code ---}.
 * <li>Subsequent {@code key: value} pairs (no nested objects, no arrays, no quoting).
 * <li>Closing {@code ---} on its own line terminates the block.
 * <li>Anything before the opening marker, between the markers, or after the closing marker is
 * preserved verbatim in the returned body.
 * </ul>
 *
 * <p>
 * Intentionally not a general YAML parser — markdown metadata stays small and predictable, and
 * avoiding a YAML dependency keeps the API surface tiny.
 */
// TODO: instead use some `Flexmark-Java` or `CommonMark-Java` libs
// `implementation 'com.vsch.flexmark:flexmark:0.64.8'` or `implementation
// 'org.commonmark:commonmark:0.22.0'`
@Deprecated(forRemoval = true)
public final class ExplainResourceFrontmatter {

  private static final String MARKER = "---";

  private ExplainResourceFrontmatter() {
  }

  public record Parsed(@NonNull Map<String, String> metadata, @NonNull String body) {
  }

  /**
   * Splits frontmatter from body. When no frontmatter is present, returns an empty metadata map and
   * the input unchanged.
   */
  @Deprecated(forRemoval = true)
  public static @NonNull Parsed parse(@NonNull String source) {
    var lines = source.split("\\R", -1);
    var firstNonEmpty = -1;
    for (var i = 0; i < lines.length; i++) {
      if (!lines[i].isBlank()) {
        firstNonEmpty = i;
        break;
      }
    }
    if (firstNonEmpty < 0 || !MARKER.equals(lines[firstNonEmpty].trim())) {
      return new Parsed(Map.of(), source);
    }
    var metadata = new LinkedHashMap<String, String>();
    var closingIdx = -1;
    for (var i = firstNonEmpty + 1; i < lines.length; i++) {
      var trimmed = lines[i].trim();
      if (MARKER.equals(trimmed)) {
        closingIdx = i;
        break;
      }
      var colon = trimmed.indexOf(':');
      if (colon < 0 || trimmed.startsWith("#")) {
        continue;
      }
      var key = trimmed.substring(0, colon).trim();
      var value = trimmed.substring(colon + 1).trim();
      if (!key.isEmpty()) {
        metadata.put(key, value);
      }
    }
    if (closingIdx < 0) {
      return new Parsed(Map.of(), source);
    }
    var bodyLines = new String[lines.length - closingIdx - 1];
    System.arraycopy(lines, closingIdx + 1, bodyLines, 0, bodyLines.length);
    var lead = 0;
    while (lead < bodyLines.length && bodyLines[lead].isBlank()) {
      lead++;
    }
    var trail = bodyLines.length;
    while (trail > lead && bodyLines[trail - 1].isBlank()) {
      trail--;
    }
    var trimmed = new String[trail - lead];
    System.arraycopy(bodyLines, lead, trimmed, 0, trimmed.length);
    var body = String.join(System.lineSeparator(), trimmed);
    return new Parsed(Map.copyOf(metadata), body);
  }
}
