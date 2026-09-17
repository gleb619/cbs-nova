package cbs.nova.dsl.builder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRepositoryTest {

  @TempDir
  Path root;

  private final FileRepository repository = new FileRepository();

  // --- resolve(): guard surface, observed through read/write/exists ---

  @Test
  void rejectsBlankRelativePath() {
    assertThatThrownBy(() -> repository.read(root, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relative path is required");
    assertThatThrownBy(() -> repository.write(root, "", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relative path is required");
    assertThatThrownBy(() -> repository.read(root, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relative path is required");
  }

  @Test
  void acceptsNormalRelativePath() throws IOException {
    Path written = repository.write(root, "dsl/LoanDsl.java", "content");

    assertThat(written).isEqualTo(root.resolve("dsl/LoanDsl.java"));
    assertThat(repository.read(root, "dsl/LoanDsl.java")).isEqualTo("content");
  }

  @Test
  void normalizesBackslashesAndRepeatedSlashes() throws IOException {
    repository.write(root, "nested\\\\deep//A.java", "a");

    assertThat(root.resolve("nested/deep/A.java")).exists();
    assertThat(repository.read(root, "nested\\deep\\A.java")).isEqualTo("a");
  }

  @Test
  void rejectsDotDotTraversal() {
    assertThatThrownBy(() -> repository.read(root, "../outside.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes workspace");
    assertThatThrownBy(() -> repository.write(root, "dsl/../../escape.txt", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes workspace");
  }

  @Test
  void rejectsDotDotTraversalViaBackslashes() {
    assertThatThrownBy(() -> repository.read(root, "..\\outside.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes workspace");
  }

  @Test
  void absoluteLookingPathIsStrippedToInRootPath() throws IOException {
    repository.write(root, "/dsl/Abs.java", "abs");

    assertThat(root.resolve("dsl/Abs.java")).exists();
  }

  // --- write(): round-trip, temp-file lifecycle, overwrite, parent dirs ---

  @Test
  void writeRoundTripsThroughRead() throws IOException {
    repository.write(root, "models/B.java", "hello");

    assertThat(repository.read(root, "models/B.java")).isEqualTo("hello");
  }

  @Test
  void writeAutoCreatesParentDirectories() throws IOException {
    Path written = repository.write(root, "a/b/c/Deep.java", "deep");

    assertThat(written.getParent()).isDirectory();
    assertThat(repository.read(root, "a/b/c/Deep.java")).isEqualTo("deep");
  }

  @Test
  void writeOverwritesExistingFile() throws IOException {
    repository.write(root, "A.java", "first");
    Path written = repository.write(root, "A.java", "second");

    assertThat(repository.read(root, "A.java")).isEqualTo("second");
    assertThat(written).isEqualTo(root.resolve("A.java"));
  }

  @Test
  void noTempFileLingersAfterSuccessfulWrite() throws IOException {
    Path dir = root.resolve("dsl");
    repository.write(root, "dsl/A.java", "a");

    try (var stream = Files.list(dir)) {
      List<String> names = stream.map(p -> p.getFileName().toString()).toList();
      assertThat(names).containsExactly("A.java");
    }
  }

  // NOTE: the AtomicMoveNotSupportedException fallback branch is not reachable on
  // filesystems that support atomic moves (e.g. the local FS behind @TempDir on
  // Linux). The fallback path is therefore untested here; the success path above
  // verifies the temp file never lingers.

  // --- list(): filtering, prefix, sorting ---

  @Test
  void listReturnsEmptyForNonDirectoryRoot(@TempDir Path temp) throws IOException {
    Path file = temp.resolve("not-a-dir.txt");
    Files.writeString(file, "x");

    assertThat(repository.list(file, null)).isEmpty();
    assertThat(repository.list(temp.resolve("missing"), null)).isEmpty();
  }

  @Test
  void listFiltersToJavaFilesOnly() throws IOException {
    repository.write(root, "A.java", "a");
    repository.write(root, "notes.txt", "t");
    repository.write(root, "dsl/B.java", "b");

    assertThat(repository.list(root, null))
            .extracting(e -> e.path())
            .containsExactly("A.java", "dsl/B.java");
  }

  @Test
  void listPrefixNarrowsResults() throws IOException {
    repository.write(root, "dsl/A.java", "a");
    repository.write(root, "models/B.java", "b");

    assertThat(repository.list(root, "dsl"))
            .extracting(e -> e.path())
            .containsExactly("dsl/A.java");
    assertThat(repository.list(root, "")).hasSize(2);
  }

  @Test
  void listIsSortedByPath() throws IOException {
    repository.write(root, "z/Last.java", "l");
    repository.write(root, "a/First.java", "f");
    repository.write(root, "Mid.java", "m");

    assertThat(repository.list(root, null))
            .extracting(e -> e.path())
            .containsExactly("Mid.java", "a/First.java", "z/Last.java");
  }

  // --- exists(): true/false plus catch-and-suppress of the guard exception ---

  @Test
  void existsTrueForRealFileFalseForMissing() throws IOException {
    repository.write(root, "A.java", "a");

    assertThat(repository.exists(root, "A.java")).isTrue();
    assertThat(repository.exists(root, "missing.java")).isFalse();
  }

  @Test
  void existsSuppressesGuardExceptionForBlankAndTraversalPaths() {
    assertThat(repository.exists(root, " ")).isFalse();
    assertThat(repository.exists(root, null)).isFalse();
    assertThat(repository.exists(root, "../escape.txt")).isFalse();
  }
}
