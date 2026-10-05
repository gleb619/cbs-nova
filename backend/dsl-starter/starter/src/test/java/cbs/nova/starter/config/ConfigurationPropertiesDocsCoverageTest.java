package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guard test: every Spring configuration key the starter binds at runtime must have its property
 * group mentioned in {@code docs/dsl/configuration.md}.
 *
 * <p>
 * Scans {@code backend/dsl-starter/starter/src/main/java/} for three sources of operator-facing
 * keys and demands that the parent path of each (everything before the last dotted segment) appear
 * in the reference doc:
 *
 * <ol>
 * <li>{@code @ConfigurationProperties(prefix = "...")} classes under
 * {@code config/properties/}.</li>
 * <li>{@code @Value("${cbs.*|dsl.*|temporal.*}")} bindings — covers keys injected through
 * {@code @Value} (e.g. process executor tunables).</li>
 * <li>{@code @ConditionalOnProperty(prefix = "...")} / {@code @ConditionalOnProperty(name = "...")}
 * — covers opt-in feature toggles whose prefix has no dedicated {@code @ConfigurationProperties}
 * class.</li>
 * </ol>
 *
 * <p>
 * Match is prefix-based and case-insensitive: a section that documents {@code cbs.foo.bar}
 * satisfies every key under it. If a key cannot be tied to a documented section, add it to
 * {@link #ALLOWED_UNDOCUMENTED} with a justification comment, or document the prefix in
 * {@code docs/dsl/configuration.md}.
 */
class ConfigurationPropertiesDocsCoverageTest {

  private static final Path SRC_MAIN = Path
          .of("src", "main", "java")
          .toAbsolutePath()
          .normalize();

  private static final Path PROPERTIES_PKG = Path
          .of("src", "main", "java", "cbs", "nova", "starter",
                  "config", "properties")
          .toAbsolutePath()
          .normalize();

  private static final Path DOC_FILE = Path
          .of("..", "..", "..", "docs", "dsl", "configuration.md")
          .toAbsolutePath()
          .normalize();

  /**
   * Keys deliberately excluded from the doc-coverage requirement. Add a justification comment for
   * each entry — a bare allowlist entry that masks a real gap is a regression.
   */
  private static final Set<String> ALLOWED_UNDOCUMENTED = Set.of(
          // cbs.dsl.files.* — no-op knobs removed by T798. Do not document; the property tree is
          // deleted when T798 lands.
          "cbs.dsl.files");

  /** Key prefixes the guard owns. Anything outside this set is ignored on purpose. */
  private static final List<String> DOCUMENTED_NAMESPACES = List.of("cbs.", "dsl.", "temporal.");

  @Test
  void everyConfigurationKeyIsDocumented() throws IOException {
    assertThat(SRC_MAIN).as("src/main/java (test runs from the starter module dir)")
            .isDirectory();
    assertThat(DOC_FILE).as("configuration reference doc").isRegularFile();

    String doc = Files.readString(DOC_FILE).toLowerCase(Locale.ROOT);

    Set<String> declared = collectAllKeys();
    List<String> missing = declared.stream()
            .filter(key -> !ALLOWED_UNDOCUMENTED.contains(key))
            .filter(key -> !doc.contains(key.toLowerCase(Locale.ROOT)))
            .toList();

    assertThat(missing)
            .withFailMessage(() -> """
                    Missing in docs/dsl/configuration.md (declared keys: %s)

                    Undocumented keys: %s

                    Source files for the first few undocumented keys:
                    %s

                    Add a section to docs/dsl/configuration.md that documents the prefix,
                    or add the key to ALLOWED_UNDOCUMENTED with a justification comment.
                    """
                    .formatted(declared, missing, sourceLocations(missing)))
            .isEmpty();
  }

  /** Walks src/main/java once and collects every key prefix the starter binds. */
  private static Set<String> collectAllKeys() throws IOException {
    Set<String> keys = new TreeSet<>();
    try (var stream = Files.walk(SRC_MAIN)) {
      List<Path> javaFiles = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".java"))
              .toList();
      for (Path path : javaFiles) {
        String source = Files.readString(path);
        // 1. @ConfigurationProperties(prefix = "...") on classes under config/properties/.
        if (path.startsWith(PROPERTIES_PKG)) {
          String prefix = extractConfigPropertiesPrefix(source);
          if (prefix != null) {
            keys.add(prefix);
          }
        }
        // 2. @Value("${cbs.X|dsl.X|temporal.X}") bindings anywhere in src/main/java.
        for (String valueKey : extractValueKeys(source)) {
          keys.add(root(valueKey));
        }
        // 3. @ConditionalOnProperty(prefix = "...") / name = "..." under the documented namespaces.
        for (String condKey : extractConditionalOnPropertyKeys(source)) {
          keys.add(root(condKey));
        }
      }
    }
    return keys;
  }

  /**
   * Maps a key back to a sorted file:line:key table so the failure message can point the operator
   * to the source of the gap. Limited to {@code maxLines} entries to keep the message readable.
   */
  private static String sourceLocations(List<String> keys) {
    int maxLines = 8;
    StringBuilder out = new StringBuilder();
    try {
      List<Path> javaFiles;
      try (var stream = Files.walk(SRC_MAIN)) {
        javaFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .toList();
      }
      List<String> wanted = new ArrayList<>(new TreeSet<>(keys));
      Set<String> seen = new HashSet<>();
      int shown = 0;
      outer : for (Path path : javaFiles) {
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          for (String want : wanted) {
            if (seen.contains(want)) {
              continue;
            }
            if (line.contains(want)) {
              String relPath = SRC_MAIN.relativize(path).toString();
              out.append("  - ").append(relPath).append(":").append(i + 1)
                      .append("  -> ").append(want).append('\n');
              seen.add(want);
              shown++;
              if (shown >= maxLines || seen.size() >= wanted.size()) {
                break outer;
              }
            }
          }
        }
      }
    } catch (IOException ignored) {
      // Best effort only.
    }
    return out.toString();
  }

  /** Returns the prefix-root (everything before the last dot) — operator keystore form. */
  private static String root(String key) {
    int lastDot = key.lastIndexOf('.');
    return lastDot < 0 ? key : key.substring(0, lastDot);
  }

  // --- @ConfigurationProperties(prefix = "...") ------------------------------

  private static String extractConfigPropertiesPrefix(String source) {
    Matcher single = SINGLE_ARG_PATTERN.matcher(source);
    if (single.find()) {
      return unwrapQuotes(single.group(1));
    }
    Matcher kv = KV_PATTERN.matcher(source);
    if (kv.find()) {
      return unwrapQuotes(kv.group(1));
    }
    return null;
  }

  // --- @Value("${key:default}") ------------------------------------------------

  private static final Pattern VALUE_KEY_PATTERN = Pattern.compile(
          "@Value\\s*\\(\\s*\"\\s*\\$\\{([\\w.\\-]+)(?::[^}]*)?}\"");

  /**
   * Yields the {@code key} part of every {@code @Value("${key[:default]}")} that lives under one of
   * {@link #DOCUMENTED_NAMESPACES}. Skips pure placeholders like {@code classpath:index.html}.
   */
  private static Set<String> extractValueKeys(String source) {
    Set<String> keys = new TreeSet<>();
    Matcher m = VALUE_KEY_PATTERN.matcher(source);
    while (m.find()) {
      String key = m.group(1).trim();
      if (DOCUMENTED_NAMESPACES.stream().anyMatch(key::startsWith)) {
        keys.add(key);
      }
    }
    return keys;
  }

  // --- @ConditionalOnProperty(prefix = "...") / name = "..." -------------------

  /**
   * Picks up both forms:
   * <ul>
   * <li>{@code @ConditionalOnProperty(prefix = "cbs.X", name = "y", ...)} → {@code cbs.X.y}</li>
   * <li>{@code @ConditionalOnProperty(name = "cbs.X.y", ...)} → {@code cbs.X.y}</li>
   * </ul>
   * Only prefixes/names under {@link #DOCUMENTED_NAMESPACES} are kept.
   */
  private static final Pattern COND_PREFIX_AND_NAME = Pattern.compile(
          "@ConditionalOnProperty\\s*\\(\\s*[^)]*prefix\\s*=\\s*\"([^\"]+)\""
                  + "[^)]*name\\s*=\\s*\"([^\"]+)\"");

  private static final Pattern COND_NAME_ONLY = Pattern.compile(
          "@ConditionalOnProperty\\s*\\(\\s*[^)]*name\\s*=\\s*\"([^\"]+)\"");

  private static final Pattern COND_PREFIX_ONLY = Pattern.compile(
          "@ConditionalOnProperty\\s*\\(\\s*prefix\\s*=\\s*\"([^\"]+)\"");

  private static Set<String> extractConditionalOnPropertyKeys(String source) {
    Set<String> keys = new TreeSet<>();
    Matcher pn = COND_PREFIX_AND_NAME.matcher(source);
    while (pn.find()) {
      String prefix = pn.group(1);
      String name = pn.group(2);
      if (DOCUMENTED_NAMESPACES.stream().anyMatch(prefix::startsWith)) {
        keys.add(prefix + "." + name);
      }
    }
    Matcher n = COND_NAME_ONLY.matcher(source);
    while (n.find()) {
      String full = n.group(1);
      if (DOCUMENTED_NAMESPACES.stream().anyMatch(full::startsWith)) {
        keys.add(full);
      }
    }
    Matcher p = COND_PREFIX_ONLY.matcher(source);
    while (p.find()) {
      String prefix = p.group(1);
      if (DOCUMENTED_NAMESPACES.stream().anyMatch(prefix::startsWith)) {
        keys.add(prefix);
      }
    }
    return keys;
  }

  // --- shared helpers ----------------------------------------------------------

  private static String unwrapQuotes(String literal) {
    if (literal.length() >= 2
            && ((literal.startsWith("\"") && literal.endsWith("\""))
                    || (literal.startsWith("'") && literal.endsWith("'")))) {
      return literal.substring(1, literal.length() - 1);
    }
    return literal;
  }

  private static final Pattern SINGLE_ARG_PATTERN = Pattern.compile(
          "@ConfigurationProperties\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");

  private static final Pattern KV_PATTERN = Pattern.compile(
          "@ConfigurationProperties\\s*\\(\\s*prefix\\s*=\\s*\"([^\"]+)\"");
}
