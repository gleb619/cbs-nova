package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guard test: every {@code @ConfigurationProperties} class declared under
 * {@code backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/properties/} must have
 * its {@code prefix} documented in {@code docs/dsl/configuration.md} (case-insensitive substring
 * match on the kebab-case key).
 *
 * <p>
 * If a new properties class is added under that package, add a matching section to
 * {@code docs/dsl/configuration.md} — or list the kebab-case prefix in
 * {@link #ALLOWED_UNDOCUMENTED} with a justification comment.
 *
 * <p>
 * The match is intentionally <em>prefix-based</em>: a single section that documents the
 * {@code cbs.foo.bar} prefix is enough to satisfy every sub-key under it.
 */
class ConfigurationPropertiesDocsCoverageTest {

  private static final Path PROPERTIES_PKG = Path
          .of("src", "main", "java", "cbs", "nova", "starter",
                  "config", "properties")
          .toAbsolutePath()
          .normalize();

  private static final Path CONFIG_DOC = Path.of("..", "..", "..", "..", "..", "docs", "dsl",
          "configuration.md")
          .toAbsolutePath()
          .normalize();

  /**
   * Prefixes deliberately excluded from the doc-coverage requirement. Add a justification comment
   * for each entry.
   */
  private static final Set<String> ALLOWED_UNDOCUMENTED = Set.of(
  // intentionally empty: docs/dsl/configuration.md is kept in lockstep with every
  // @ConfigurationProperties class under config/properties/.
  // Add entries here only when a class must ship without a doc entry,
  // and document why in the comment above each name.
  );

  @Test
  void everyConfigurationPropertiesPrefixIsDocumented() throws IOException {
    assumeTrue(Files.isDirectory(PROPERTIES_PKG),
            () -> "properties package not found at " + PROPERTIES_PKG
                    + " (running outside the repo layout); skipping doc-drift check");
    assumeTrue(Files.exists(CONFIG_DOC),
            () -> "docs/dsl/configuration.md not found at " + CONFIG_DOC
                    + " (running outside the repo layout); skipping doc-drift check");

    String doc = Files.readString(CONFIG_DOC).toLowerCase(Locale.ROOT);

    Set<String> declaredPrefixes = collectDeclaredPrefixes();
    List<String> missing = declaredPrefixes.stream()
            .filter(prefix -> !ALLOWED_UNDOCUMENTED.contains(prefix))
            .filter(prefix -> !doc.contains(prefix.toLowerCase(Locale.ROOT)))
            .toList();

    assertThat(missing)
            .as("@ConfigurationProperties prefix(es) not documented in docs/dsl/configuration.md"
                    + " (declared prefixes: %s)"
                    + " — add a section to docs/dsl/configuration.md"
                    + " or add the prefix to ALLOWED_UNDOCUMENTED with justification",
                    declaredPrefixes)
            .isEmpty();
  }

  /**
   * Returns the set of {@code @ConfigurationProperties} prefixes declared under the properties
   * package, in kebab-case. Scans source files directly (rather than loading classes) so a typo in
   * one class cannot break the test compilation.
   */
  private static Set<String> collectDeclaredPrefixes() throws IOException {
    Set<String> prefixes = new TreeSet<>();
    try (var stream = Files.walk(PROPERTIES_PKG)) {
      List<Path> sourceFiles = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".java"))
              .toList();
      for (Path path : sourceFiles) {
        String source = Files.readString(path);
        String prefix = extractPrefix(source);
        if (prefix != null) {
          prefixes.add(prefix);
        }
      }
    }
    return prefixes;
  }

  /**
   * Extracts the prefix from an {@code @ConfigurationProperties} annotation. Supports both
   * {@code @ConfigurationProperties(prefix = "...")} and the single-argument
   * {@code @ConfigurationProperties("...")} form.
   */
  private static String extractPrefix(String source) {
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
