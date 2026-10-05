package cbs.nova.dsl.builder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
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

  @Test
  void existsIsFalseForSymlinkedLeafEscapingRoot() throws IOException {
    assumeSymlinksSupported();
    Path outside = Files.createTempDirectory("outside-file-");
    Path secret = outside.resolve("Secret.java");
    Files.writeString(secret, "secret");
    try {
      Files.createSymbolicLink(root.resolve("Leak.java"), secret);

      assertThat(repository.exists(root, "Leak.java")).isFalse();
    } finally {
      deleteRecursively(outside);
    }
  }

  // --- symlink containment (real-path checks) ---

  @Test
  void readRejectsSymlinkedFilePointingOutsideRoot() throws IOException {
    assumeSymlinksSupported();
    Path outside = Files.createTempDirectory("outside-file-");
    Path secret = outside.resolve("Secret.java");
    Files.writeString(secret, "secret");
    try {
      Files.createSymbolicLink(root.resolve("Leak.java"), secret);

      assertThatThrownBy(() -> repository.read(root, "Leak.java"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("escapes workspace via symlink");
    } finally {
      deleteRecursively(outside);
    }
  }

  @Test
  void writeRejectsSymlinkedFilePointingOutsideRootAndLeavesTargetUntouched()
          throws IOException {
    assumeSymlinksSupported();
    Path outside = Files.createTempDirectory("outside-file-");
    Path secret = outside.resolve("Secret.java");
    Files.writeString(secret, "original");
    try {
      Files.createSymbolicLink(root.resolve("Leak.java"), secret);

      assertThatThrownBy(() -> repository.write(root, "Leak.java", "overwritten"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("escapes workspace via symlink");
      assertThat(Files.readString(secret)).isEqualTo("original");
    } finally {
      deleteRecursively(outside);
    }
  }

  @Test
  void readAndWriteRejectSymlinkedDirectoryPointingOutsideRoot() throws IOException {
    assumeSymlinksSupported();
    Path outside = Files.createTempDirectory("outside-dir-");
    Files.createDirectories(outside.resolve("dsl"));
    Files.writeString(outside.resolve("dsl/Sneaky.java"), "sneaky");
    try {
      Files.createSymbolicLink(root.resolve("dsl"), outside.resolve("dsl"));

      assertThatThrownBy(() -> repository.read(root, "dsl/Sneaky.java"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("escapes workspace via symlink");
      assertThatThrownBy(() -> repository.write(root, "dsl/Planted.java", "planted"))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("escapes workspace via symlink");
      assertThat(outside.resolve("dsl/Planted.java")).doesNotExist();
    } finally {
      deleteRecursively(outside);
    }
  }

  @Test
  void listSkipsSymlinkedEntriesResolvingOutsideRoot() throws IOException {
    assumeSymlinksSupported();
    Path outside = Files.createTempDirectory("outside-list-");
    Files.writeString(outside.resolve("Leaked.java"), "leaked");
    try {
      Files.createSymbolicLink(root.resolve("Leaked.java"), outside.resolve("Leaked.java"));
      Files.createSymbolicLink(root.resolve("dsl"), outside);
      repository.write(root, "Real.java", "real");

      assertThat(repository.list(root, null))
              .extracting(e -> e.path())
              .containsExactly("Real.java");
    } finally {
      deleteRecursively(outside);
    }
  }

  /**
   * Documented decision: links that stay inside the workspace are allowed for read and for the
   * containment check. Note that a write replaces the link itself (atomic move over the link), so
   * it does not follow the link — the previous target keeps its content.
   */
  @Test
  void symlinkStayingInsideRootIsAllowed() throws IOException {
    assumeSymlinksSupported();
    repository.write(root, "real/Target.java", "inside");
    Files.createSymbolicLink(root.resolve("Alias.java"), root.resolve("real/Target.java"));

    assertThat(repository.read(root, "Alias.java")).isEqualTo("inside");
    assertThat(repository.exists(root, "Alias.java")).isTrue();
    assertThat(repository.list(root, null))
            .extracting(e -> e.path())
            .contains("real/Target.java")
            .doesNotContain("Alias.java");

    repository.write(root, "Alias.java", "updated");

    assertThat(repository.read(root, "Alias.java")).isEqualTo("updated");
    assertThat(repository.read(root, "real/Target.java")).isEqualTo("inside");
  }

  @Test
  void plainNestedPathStillWorksAfterRealPathCheck() throws IOException {
    repository.write(root, "a/b/c/Deep.java", "deep");

    assertThat(repository.read(root, "a/b/c/Deep.java")).isEqualTo("deep");
    assertThat(repository.exists(root, "a/b/c/Deep.java")).isTrue();
    assertThat(repository.list(root, null))
            .extracting(e -> e.path())
            .containsExactly("a/b/c/Deep.java");
  }

  private static void assumeSymlinksSupported() {
    assumeTrue(symlinksSupported(), "filesystem does not support symbolic links");
  }

  private static boolean symlinksSupported() {
    Path probe = Path.of(System.getProperty("java.io.tmpdir"));
    Path link = probe.resolve("cbs-nova-symlink-probe-" + System.nanoTime());
    try {
      Files.createSymbolicLink(link, probe);
      boolean created = Files.isSymbolicLink(link);
      Files.deleteIfExists(link);
      return created;
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      return false;
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          // best-effort cleanup of temp fixtures
        }
      });
    }
  }
}
