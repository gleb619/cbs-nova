package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Guard test: every {@code *Dsl.java} example under {@code dsl-examples/src/dsl/} must be mentioned
 * (case-insensitive substring of its class name) in BOTH {@code docs/dsl/examples.md} AND
 * {@code backend/dsl-starter/dsl-examples/README.md}.
 *
 * <p>
 * If a new example is added to {@code dsl-examples/src/dsl/}, add a matching section to both doc
 * files — or list the class name in {@link #ALLOWED_UNDOCUMENTED} with a justification comment.
 */
class ExamplesDocsCoverageTest {

  private static final Path EXAMPLES_SRC_DIR = Path.of("..", "dsl-examples", "src", "dsl")
          .toAbsolutePath()
          .normalize();

  private static final Path EXAMPLES_DOC = Path.of("..", "..", "..", "docs", "dsl", "examples.md")
          .toAbsolutePath()
          .normalize();

  private static final Path EXAMPLES_README = Path.of("..", "dsl-examples", "README.md")
          .toAbsolutePath()
          .normalize();

  /**
   * Class names deliberately excluded from the doc-coverage requirement. Add a justification
   * comment for each entry.
   */
  private static final Set<String> ALLOWED_UNDOCUMENTED = Set.of(
  // intentionally empty: docs/dsl/examples.md and dsl-examples/README.md
  // are kept in lockstep with every *Dsl.java under dsl-examples/src/dsl/.
  // Add entries here only when a class must ship without a doc entry,
  // and document why in the comment above each name.
  );

  @Test
  void everyExampleDslClassIsDocumentedInBothFiles() throws IOException {
    assumeTrue(Files.isDirectory(EXAMPLES_SRC_DIR),
            () -> "examples source dir not found at " + EXAMPLES_SRC_DIR
                    + " (running outside the repo layout); skipping doc-drift check");
    assumeTrue(Files.exists(EXAMPLES_DOC),
            () -> "docs/dsl/examples.md not found at " + EXAMPLES_DOC
                    + " (running outside the repo layout); skipping doc-drift check");
    assumeTrue(Files.exists(EXAMPLES_README),
            () -> "dsl-examples/README.md not found at " + EXAMPLES_README
                    + " (running outside the repo layout); skipping doc-drift check");

    String examplesDoc = Files.readString(EXAMPLES_DOC).toLowerCase(Locale.ROOT);
    String examplesReadme = Files.readString(EXAMPLES_README).toLowerCase(Locale.ROOT);

    List<String> classNames = listExampleDslClassNames(EXAMPLES_SRC_DIR);

    List<String> missing = classNames.stream()
            .filter(name -> !ALLOWED_UNDOCUMENTED.contains(name))
            .filter(name -> {
              String needle = name.toLowerCase(Locale.ROOT);
              return !examplesDoc.contains(needle) || !examplesReadme.contains(needle);
            })
            .toList();

    assertThat(missing)
            .as("Example DSL class(es) not mentioned in BOTH docs/dsl/examples.md AND"
                    + " backend/dsl-starter/dsl-examples/README.md (checked class names: %s)"
                    + " — add a section to both doc files"
                    + " or add the class name to ALLOWED_UNDOCUMENTED with justification",
                    classNames)
            .isEmpty();
  }

  private static List<String> listExampleDslClassNames(Path sourceDir) throws IOException {
    try (Stream<Path> stream = Files.walk(sourceDir)) {
      return stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith("Dsl.java"))
              .map(p -> p.getFileName().toString().substring(0,
                      p.getFileName().toString().length() - ".java".length()))
              .sorted()
              .toList();
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }
}
