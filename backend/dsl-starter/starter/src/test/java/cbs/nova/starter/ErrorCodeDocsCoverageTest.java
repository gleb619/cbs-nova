package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.config.Constants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guard test for T830: every error code the starter can emit must be documented in
 * {@code docs/api-errors.md}. Mirrors {@link HelperDocsCoverageTest}.
 */
class ErrorCodeDocsCoverageTest {

  private static final Path REPO_ROOT = Path.of("").toAbsolutePath()
          .normalize()
          .getParent()
          .getParent()
          .getParent();
  private static final Path API_ERRORS_DOC = REPO_ROOT.resolve("docs/api-errors.md");
  private static final Path STARTER_MAIN = REPO_ROOT
          .resolve("backend/dsl-starter/starter/src/main/java");

  /**
   * Justifications for code groups that are emitted dynamically and cannot be enumerated as a
   * closed set. The doc must contain each note so operators know why the catalog does not list
   * every possible value.
   */
  private static final Set<String> ALLOWED_UNDOCUMENTED_NOTES = Set.of(
          "upstream builder error codes are **not a closed set**",
          "first error's `code`");

  @Test
  void everyEmittedErrorCodeIsDocumented() throws IOException {
    assertThat(API_ERRORS_DOC).exists();
    String doc = Files.readString(API_ERRORS_DOC).toLowerCase(Locale.ROOT);

    Set<String> codes = new TreeSet<>();
    codes.addAll(enumCodes());
    codes.add(Constants.FALLBACK_CODE);
    codes.addAll(scannedLiteralCodes());

    List<String> missing = codes.stream()
            .filter(code -> !doc.contains(code.toLowerCase(Locale.ROOT)))
            .toList();

    assertThat(missing)
            .as("Error code(s) emitted by the starter but missing from docs/api-errors.md"
                    + " — add the code to the catalog or to ALLOWED_UNDOCUMENTED_NOTES with"
                    + " justification")
            .isEmpty();

    for (String note : ALLOWED_UNDOCUMENTED_NOTES) {
      assertThat(doc)
              .as("docs/api-errors.md must acknowledge the open/dynamic code group: %s", note)
              .contains(note.toLowerCase(Locale.ROOT));
    }
  }

  private static Set<String> enumCodes() {
    Set<String> result = new HashSet<>();
    for (DslErrorCode value : DslErrorCode.values()) {
      result.add(value.name());
    }
    for (PreviewErrorCode value : PreviewErrorCode.values()) {
      result.add(value.name());
    }
    return result;
  }

  private static Set<String> scannedLiteralCodes() throws IOException {
    Set<String> literals = new HashSet<>();
    Map<String, String> constants = new HashMap<>();

    Pattern constPattern = Pattern.compile(
            "^\\s*(?:public|private|protected|static|final|\\s)*String\\s+([A-Z_][A-Z0-9_]*)\\s*=\\s*\"([^\"]+)\"\\s*;",
            Pattern.MULTILINE);
    Pattern literalPattern = Pattern.compile(
            "new\\s+ErrorResponse(?:Context)?\\s*\\(\\s*\"([^\"]+)\"");
    Pattern identPattern = Pattern.compile(
            "new\\s+ErrorResponse(?:Context)?\\s*\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)*([A-Z_][A-Z0-9_]*)");

    try (Stream<Path> files = Files.walk(STARTER_MAIN)) {
      List<Path> javaFiles = files
              .filter(p -> p.toString().endsWith(".java"))
              .toList();
      for (Path file : javaFiles) {
        String source = Files.readString(file);
        Matcher constMatcher = constPattern.matcher(source);
        while (constMatcher.find()) {
          constants.put(constMatcher.group(1), constMatcher.group(2));
        }
      }
      for (Path file : javaFiles) {
        String source = Files.readString(file);
        Matcher literalMatcher = literalPattern.matcher(source);
        while (literalMatcher.find()) {
          literals.add(literalMatcher.group(1));
        }
        Matcher identMatcher = identPattern.matcher(source);
        while (identMatcher.find()) {
          String value = constants.get(identMatcher.group(1));
          if (value != null) {
            literals.add(value);
          }
        }
      }
    }

    return literals;
  }
}
