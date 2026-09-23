package cbs.nova.dsl.explain;

import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterBlock;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Splits an YAML frontmatter block from the body of an explain markdown file.
 *
 * <p>
 * The implementation delegates to the {@code flexmark-ext-yaml-front-matter} extension, which is
 * the canonical Java parser for the {@code --- ... ---} metadata block used by markdown static-site
 * generators. Frontmatter is parsed structurally (no regex on raw text); quoting around values is
 * stripped to mirror YAML semantics; comments inside the block are ignored.
 *
 * <p>
 * Lenient fallbacks match the previous hand-rolled reader and are pinned by tests:
 * <ul>
 * <li>no opening {@code ---} at the start of the document — empty metadata and the source returned
 * unchanged;
 * <li>opening {@code ---} without a matching closing marker — treated as no frontmatter;
 * <li>body is trimmed of leading/trailing blank lines.
 * </ul>
 */
public final class ExplainResourceFrontmatter {

  private static final Parser PARSER = Parser.builder()
          .extensions(List.of(YamlFrontMatterExtension.create()))
          .build();

  private ExplainResourceFrontmatter() {
  }

  public record Parsed(@NonNull Map<String, String> metadata, @NonNull String body) {
  }

  /**
   * Splits frontmatter from body. When no frontmatter is present, returns an empty metadata map and
   * the input unchanged.
   */
  public static @NonNull Parsed parse(@NonNull String source) {
    var document = PARSER.parse(source);

    var yamlBlock = firstYamlBlock(document);
    if (yamlBlock == null) {
      return new Parsed(Map.of(), source);
    }

    // flexmark still emits a YamlFrontMatterBlock for an opening `---` without a matching
    // closing marker; treat that case as "no frontmatter" to preserve the lenient behaviour
    // pinned by ExplainResourceFrontmatterTest.
    if (!hasClosingMarker(source)) {
      return new Parsed(Map.of(), source);
    }

    var visitor = new AbstractYamlFrontMatterVisitor();
    visitor.visit(document);

    var metadata = new LinkedHashMap<String, String>();
    for (var entry : visitor.getData().entrySet()) {
      metadata.put(entry.getKey(), unquote(joinValues(entry.getValue())));
    }

    return new Parsed(Map.copyOf(metadata), extractBody(source));
  }

  private static YamlFrontMatterBlock firstYamlBlock(@NonNull Node document) {
    for (var child : document.getChildren()) {
      if (child instanceof YamlFrontMatterBlock block) {
        return block;
      }
    }
    return null;
  }

  private static boolean hasClosingMarker(@NonNull String source) {
    var lines = source.split("\\R", -1);
    var openingIdx = firstNonBlank(lines);
    if (openingIdx < 0 || !"---".equals(lines[openingIdx].trim())) {
      return false;
    }
    for (var i = openingIdx + 1; i < lines.length; i++) {
      if ("---".equals(lines[i].trim())) {
        return true;
      }
    }
    return false;
  }

  private static @NonNull String extractBody(@NonNull String source) {
    var lines = source.split("\\R", -1);
    var openingIdx = firstNonBlank(lines);
    var closingIdx = -1;
    for (var i = openingIdx + 1; i < lines.length; i++) {
      if ("---".equals(lines[i].trim())) {
        closingIdx = i;
        break;
      }
    }
    if (closingIdx < 0) {
      return source;
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
    return String.join(System.lineSeparator(), trimmed);
  }

  private static int firstNonBlank(@NonNull String[] lines) {
    for (var i = 0; i < lines.length; i++) {
      if (!lines[i].isBlank()) {
        return i;
      }
    }
    return -1;
  }

  private static @NonNull String joinValues(@NonNull List<String> values) {
    return values.isEmpty() ? "" : String.join("\n", values);
  }

  /** Strips a single layer of matching {@code "..."} or {@code '...'} wrapping from a value. */
  private static @NonNull String unquote(@NonNull String value) {
    if (value.length() < 2) {
      return value;
    }
    var first = value.charAt(0);
    var last = value.charAt(value.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
