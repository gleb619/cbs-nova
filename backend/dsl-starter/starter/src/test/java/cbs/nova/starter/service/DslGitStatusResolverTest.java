package cbs.nova.starter.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Direct unit coverage for {@link DslGitStatusResolver}. The resolver caches by resolved repository
 * root, so each test gets a fresh {@code @TempDir} AND a fresh resolver instance to keep the
 * in-memory cache empty.
 */
class DslGitStatusResolverTest {

  private static final PersonIdent IDENT = new PersonIdent("t420", "t420@example.test");

  @TempDir
  Path tempDir;

  private DslGitStatusResolver resolver;

  @BeforeEach
  void setUp() {
    // Default: git enabled, default TTL (5s), no configured repositoryDir.
    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, null, 5))
            .build();
    resolver = newResolver(props);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> s = Files.walk(tempDir)) {
        s.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Gate: git disabled / no repository
  // ---------------------------------------------------------------------------------------------
  //
  // Note: the `git() == null` branch in DslGitStatusResolver is unreachable in practice —
  // DslProperties' compact constructor coalesces a null Git to the default Git(true, null, 5),
  // so we cannot construct a DslProperties instance whose `git()` returns null. The defensive
  // null-check stays in the resolver as a safety net, but it has no test.

  @Test
  void statusReturnsEmptyWhenGitIsDisabled() throws IOException {
    Files.createDirectories(tempDir.resolve(".git")); // repository exists, but disabled overrides
    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(false, null, 5))
            .build();
    DslGitStatusResolver r = newResolver(props);

    Optional<RepoStatus> result = r.status(tempDir);

    assertThat(result).isEmpty();
  }

  @Test
  void statusReturnsEmptyWhenNoGitRepositoryFound() {
    // tempDir has no .git directory at all — resolver must log + return empty, not throw.
    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Clean repo baseline
  // ---------------------------------------------------------------------------------------------

  @Test
  void cleanFreshRepoProducesEmptyDirtyPathsAndAbsoluteNormalizedWorkTree() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isPresent();
    RepoStatus status = result.get();
    assertThat(status.dirtyPaths()).isEmpty();
    assertThat(status.workTree()).isEqualTo(tempDir.toRealPath());
    assertThat(status.workTree().isAbsolute()).isTrue();
    // Normalized form must not contain "." or ".." segments.
    assertThat(status.workTree().toString()).doesNotContain("/./").doesNotContain("/..");
  }

  // ---------------------------------------------------------------------------------------------
  // Dirty buckets — one test per bucket the resolver aggregates
  // ---------------------------------------------------------------------------------------------

  @Test
  void untrackedFileSurfacesInDirtyPaths() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");
    Files.writeString(tempDir.resolve("untracked.txt"), "hello", UTF_8);

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isPresent();
    assertThat(result.get().dirtyPaths()).containsExactly("untracked.txt");
  }

  @Test
  void modifiedTrackedFileSurfacesInDirtyPaths() throws Exception {
    initRepo(tempDir);
    Files.writeString(tempDir.resolve("tracked.txt"), "v1", UTF_8);
    commitAll(tempDir, "initial");
    Files.writeString(tempDir.resolve("tracked.txt"), "v2", UTF_8);

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isPresent();
    assertThat(result.get().dirtyPaths()).containsExactly("tracked.txt");
  }

  @Test
  void stagedAddSurfacesInDirtyPaths() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");
    Files.writeString(tempDir.resolve("staged.txt"), "new", UTF_8);
    try (Git git = Git.open(tempDir.toFile())) {
      git.add().addFilepattern("staged.txt").call();
    }

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isPresent();
    assertThat(result.get().dirtyPaths()).containsExactly("staged.txt");
  }

  @Test
  void deletedTrackedFileSurfacesInDirtyPaths() throws Exception {
    initRepo(tempDir);
    Files.writeString(tempDir.resolve("doomed.txt"), "bye", UTF_8);
    commitAll(tempDir, "initial");
    Files.delete(tempDir.resolve("doomed.txt"));

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isPresent();
    // JGit reports a tracked-but-deleted-from-worktree file under getMissing().
    assertThat(result.get().dirtyPaths()).containsExactly("doomed.txt");
  }

  // ---------------------------------------------------------------------------------------------
  // Config resolution: repositoryDir overrides candidateDir
  // ---------------------------------------------------------------------------------------------

  @Test
  void configuredRepositoryDirOverridesCandidateDir() throws Exception {
    // Real repo lives at tempDir, but config tells the resolver to look at a different root.
    initRepo(tempDir);
    commitAll(tempDir, "initial");
    Files.writeString(tempDir.resolve("dirty.txt"), "x", UTF_8);

    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, tempDir.toString(), 0))
            .build();
    DslGitStatusResolver r = newResolver(props);

    // candidateDir points somewhere with no repo — resolver must still find the configured one
    // and report the dirty file there.
    Path decoy = Files.createTempDirectory("dsl-decoy-");
    try {
      Optional<RepoStatus> result = r.status(decoy);

      assertThat(result).isPresent();
      assertThat(result.get().workTree()).isEqualTo(tempDir.toRealPath());
      assertThat(result.get().dirtyPaths()).containsExactly("dirty.txt");
    } finally {
      deleteRecursively(decoy);
    }
  }

  @Test
  void nullRepositoryDirFallsBackToCandidateDir() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");

    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, null, 5))
            .build();
    DslGitStatusResolver r = newResolver(props);

    Optional<RepoStatus> result = r.status(tempDir);

    assertThat(result).isPresent();
    assertThat(result.get().workTree()).isEqualTo(tempDir.toRealPath());
  }

  @Test
  void blankRepositoryDirFallsBackToCandidateDir() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");

    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, "   ", 5))
            .build();
    DslGitStatusResolver r = newResolver(props);

    Optional<RepoStatus> result = r.status(tempDir);

    assertThat(result).isPresent();
    assertThat(result.get().workTree()).isEqualTo(tempDir.toRealPath());
  }

  // ---------------------------------------------------------------------------------------------
  // Cache TTL behaviour
  // ---------------------------------------------------------------------------------------------

  @Test
  void zeroTtlForcesFreshScanOnEveryCall() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");

    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, null, 0))
            .build();
    DslGitStatusResolver r = newResolver(props);

    Optional<RepoStatus> first = r.status(tempDir);
    assertThat(first).isPresent();
    assertThat(first.get().dirtyPaths()).isEmpty();

    // Mutate the repo after the first scan. With TTL=0 the cached entry is already expired,
    // so the next call must re-scan and pick up the new dirty file.
    Files.writeString(tempDir.resolve("late.txt"), "late", UTF_8);

    Optional<RepoStatus> second = r.status(tempDir);
    assertThat(second).isPresent();
    assertThat(second.get().dirtyPaths()).containsExactly("late.txt");
  }

  @Test
  void nonZeroTtlReturnsStaleSnapshotWithinTtl() throws Exception {
    initRepo(tempDir);
    commitAll(tempDir, "initial");

    DslProperties props = DslProperties.builder()
            .git(new DslProperties.Git(true, null, 60))
            .build();
    DslGitStatusResolver r = newResolver(props);

    Optional<RepoStatus> first = r.status(tempDir);
    assertThat(first).isPresent();
    assertThat(first.get().dirtyPaths()).isEmpty();

    // Mutate after caching — within the TTL the cached snapshot is reused.
    Files.writeString(tempDir.resolve("within-ttl.txt"), "x", UTF_8);

    Optional<RepoStatus> second = r.status(tempDir);
    assertThat(second).isPresent();
    assertThat(second.get().dirtyPaths())
            .as("TTL=60s should still hold the stale empty-dirty snapshot")
            .isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Failure fallback
  // ---------------------------------------------------------------------------------------------

  @Test
  void corruptGitDirReturnsEmptyWithoutThrowing() throws IOException {
    // Force loadStatus() to fail inside the try/catch: drop a regular file where .git must be
    // a directory. FileRepositoryBuilder.findGitDir() will not resolve a non-directory entry,
    // so the resolver throws IOException("no git repository found under ...") and the outer
    // catch returns Optional.empty() with a warn log.
    Files.writeString(tempDir.resolve(".git"), "this is not a directory", UTF_8);

    Optional<RepoStatus> result = resolver.status(tempDir);

    assertThat(result).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private static DslGitStatusResolver newResolver(DslProperties props) {
    return new DslGitStatusResolver(
            props,
            EmptyObjectProvider.of(cbs.nova.starter.builder.DslBuilderClient.class));
  }

  private static void initRepo(Path dir) throws Exception {
    Git.init().setDirectory(dir.toFile()).call().close();
  }

  private static void commitAll(Path dir, String message) throws Exception {
    try (Git git = Git.open(dir.toFile())) {
      git.add().addFilepattern(".").call();
      git.commit().setAuthor(IDENT).setCommitter(IDENT).setMessage(message).call();
    }
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> s = Files.walk(root)) {
      s.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
      });
    }
  }
}
