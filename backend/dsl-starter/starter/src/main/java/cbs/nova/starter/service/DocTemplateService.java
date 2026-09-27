package cbs.nova.starter.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Canonical skeleton for DSL documentation markdown.
 *
 * <p>
 * Every doc carries its construct {@code name} twice: once in the YAML frontmatter (the key the
 * Workbench searches and the {@code by-name} endpoints resolve against) and once as the leading
 * {@code # <name>} heading of the body (the title readers see). {@link DocNameResolver} owns the
 * name-to-path mapping; this class owns the name-to-content mapping, so the Workbench UI and the
 * HTTP write path cannot drift apart.
 *
 * <p>
 * The parser is intentionally minimal: docs are flat {@code key: value} frontmatter written by the
 * UI (see {@code serializeMarkdownDoc} in the component library). Anything that does not parse as a
 * flat scalar is preserved verbatim in {@code extras} so a hand-written doc survives a round trip.
 */
@Component
public class DocTemplateService {

  /** Frontmatter key carrying the construct name — the doc's search key. */
  public static final String NAME_KEY = "name";

  /** Frontmatter key carrying the one-line summary. */
  public static final String DESCRIPTION_KEY = "description";

  private static final String FENCE = "---";
  private static final String HEADING_PREFIX = "# ";

  /**
   * Builds a brand-new doc for {@code name}: frontmatter with {@code name}/{@code description} and
   * a body whose first line is {@code # <name>}.
   *
   * @param name
   *          construct name; a blank name yields an empty document.
   * @return markdown with a trailing newline.
   */
  public String newDoc(String name) {
    return newDoc(name, "");
  }

  /**
   * @param name
   *          construct name.
   * @param description
   *          one-line summary written to the {@code description} frontmatter key; may be blank.
   * @see #newDoc(String)
   */
  public String newDoc(String name, String description) {
    String trimmedName = name == null ? "" : name.trim();
    if (trimmedName.isEmpty()) {
      return "";
    }
    return serialize(frontmatter(trimmedName, description), HEADING_PREFIX + trimmedName + "\n");
  }

  /**
   * Fills the gaps of a doc that is being created for the first time: a missing frontmatter block
   * gets {@code name}/{@code description}, and a body that does not open with a {@code # } heading
   * gets one. Content that already carries either part is left untouched.
   *
   * <p>
   * A doc that already declares its own {@code name} keeps it — the form-mode name field is
   * user-editable and the request name is only the fallback for a doc that has none yet. Letting
   * the request name win would silently revert a rename on the next save and leave the frontmatter
   * and the body heading disagreeing.
   *
   * @param name
   *          construct name from the request, used as fallback only.
   * @param content
   *          client-supplied markdown; blank is treated as an empty body.
   * @return markdown guaranteed to carry {@code name} in the frontmatter and a leading {@code # }
   *         section, plus a trailing newline.
   */
  public String ensureDocStructure(String name, String content) {
    String trimmedName = name == null ? "" : name.trim();
    String raw = content == null ? "" : content;
    if (trimmedName.isEmpty()) {
      return raw;
    }
    Parsed parsed = parse(raw);
    String docName = parsed.frontmatter.getOrDefault(NAME_KEY, "").trim();
    if (docName.isEmpty()) {
      docName = trimmedName;
    }
    Map<String, String> frontmatter = new LinkedHashMap<>();
    frontmatter.put(NAME_KEY, docName);
    frontmatter.put(DESCRIPTION_KEY, parsed.frontmatter.getOrDefault(DESCRIPTION_KEY, ""));
    // Preserve any other frontmatter keys the user (or an older template) wrote.
    for (Map.Entry<String, String> entry : parsed.frontmatter.entrySet()) {
      if (!NAME_KEY.equals(entry.getKey()) && !DESCRIPTION_KEY.equals(entry.getKey())) {
        frontmatter.put(entry.getKey(), entry.getValue());
      }
    }
    String body = parsed.body;
    if (!body.stripLeading().startsWith(HEADING_PREFIX)) {
      body = HEADING_PREFIX + docName + (body.isEmpty() ? "\n" : "\n\n" + body);
    }
    return serialize(frontmatter, body);
  }

  private static Map<String, String> frontmatter(String name, String description) {
    Map<String, String> frontmatter = new LinkedHashMap<>();
    frontmatter.put(NAME_KEY, name);
    frontmatter.put(DESCRIPTION_KEY, description == null ? "" : description.trim());
    return frontmatter;
  }

  private static String serialize(Map<String, String> frontmatter, String body) {
    StringBuilder out = new StringBuilder();
    out.append(FENCE).append('\n');
    frontmatter.forEach(
            (String key, String value) -> out.append(key).append(": ").append(formatScalar(value))
                    .append('\n'));
    out.append(FENCE).append('\n');
    String trimmedBody = stripTrailingNewlines(body);
    if (!trimmedBody.isEmpty()) {
      out.append('\n').append(trimmedBody).append('\n');
    }
    return out.toString();
  }

  private static String stripTrailingNewlines(String value) {
    int end = value.length();
    while (end > 0 && (value.charAt(end - 1) == '\n' || value.charAt(end - 1) == '\r')) {
      end--;
    }
    return value.substring(0, end);
  }

  private record Parsed(Map<String, String> frontmatter, String body) {
  }

  private static Parsed parse(String content) {
    String normalized = content.replace("﻿", "");
    List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
    boolean fenced = !lines.isEmpty() && FENCE.equals(lines.get(0).trim());
    int closingIndex = -1;
    if (fenced) {
      for (int i = 1; i < lines.size(); i++) {
        if (FENCE.equals(lines.get(i).trim())) {
          closingIndex = i;
          break;
        }
      }
    }
    if (!fenced || closingIndex == -1) {
      return new Parsed(Map.of(), normalized);
    }
    Map<String, String> frontmatter = new LinkedHashMap<>();
    for (String line : lines.subList(1, closingIndex)) {
      int separator = line.indexOf(':');
      if (separator <= 0) {
        continue;
      }
      String key = line.substring(0, separator).trim();
      if (!key.matches("[A-Za-z0-9_-]+")) {
        continue;
      }
      frontmatter.put(key, unquote(line.substring(separator + 1).trim()));
    }
    String body = String.join("\n", lines.subList(closingIndex + 1, lines.size()));
    return new Parsed(frontmatter, body.replaceFirst("^\\n+", ""));
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      String inner = value.substring(1, value.length() - 1);
      return inner.replace("\\\"", "\"").replace("\\\\", "\\");
    }
    if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
      return value.substring(1, value.length() - 1).replace("''", "'");
    }
    return value;
  }

  /**
   * Mirrors the component library's {@code formatYamlScalar}: plain when the value is a safe bare
   * scalar, JSON-quoted otherwise (empty values included, so the key is still present).
   */
  private static String formatScalar(String value) {
    String raw = value == null ? "" : value;
    boolean plainSafe = !raw.isEmpty() && isPlainStart(raw.charAt(0)) && raw.indexOf('\n') < 0
            && !raw.contains(": ") && !raw.contains(" #") && raw.trim().equals(raw);
    return plainSafe ? raw : quote(raw);
  }

  /** Mirrors the component library's {@code /^[A-Za-z0-9_-]/} leading-character check. */
  private static boolean isPlainStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'
            || c == '-';
  }

  private static String quote(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
