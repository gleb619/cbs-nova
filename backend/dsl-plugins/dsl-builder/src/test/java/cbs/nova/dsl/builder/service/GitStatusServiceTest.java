package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.service.GitStatusService.ChangeType;
import cbs.nova.dsl.builder.service.GitStatusService.RepoStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitStatusServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void returnsEmptyWhenGitDisabled() {
    var service = newService(git(false, null, 60), mutableClock());

    assertThat(service.status(tempDir)).isEmpty();
  }

  @Test
  void returnsEmptyForNullCandidate() {
    var service = newService(git(true, null, 60), mutableClock());

    assertThat(service.status(null)).isEmpty();
  }

  @Test
  void returnsEmptyWhenCandidateIsNotARepository() throws Exception {
    var notARepo = tempDir.resolve("not-a-repo-" + UUID.randomUUID());
    Files.createDirectories(notARepo);
    var service = newService(git(true, null, 60), mutableClock());

    assertThat(service.status(notARepo)).isEmpty();
  }

  @Test
  void reportsCleanRepository() throws Exception {
    Path repo = initRepo();
    var service = newService(git(true, null, 60), mutableClock());

    Optional<GitStatusService.RepoStatus> status = service.status(repo);

    assertThat(status).isPresent();
    assertThat(status.get().dirtyPaths()).isEmpty();
    assertThat(status.get().workTree()).isEqualTo(repo.toRealPath());
  }

  @Test
  void reportsModifiedUntrackedAndStagedPaths() throws Exception {
    Path repo = initRepo();

    // Modify tracked file (set HEAD content to "v1", working tree to "v2").
    Files.writeString(repo.resolve("README.md"), "v2");

    // Stage a new file.
    Files.writeString(repo.resolve("added.txt"), "added");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("added.txt").call();
    }

    // Untracked file (never staged).
    Files.writeString(repo.resolve("scratch.txt"), "scratch");

    var service = newService(git(true, null, 60), mutableClock());

    GitStatusService.RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.dirtyPaths())
            .contains("README.md", "added.txt", "scratch.txt");
  }

  @Test
  void cacheHitDoesNotRescanWithinTtl() throws Exception {
    Path repo = initRepo();
    var clock = mutableClock();
    var service = newService(git(true, null, 60), clock);

    GitStatusService.RepoStatus first = service.status(repo).orElseThrow();
    assertThat(first.dirtyPaths()).isEmpty();

    // Mutate the repo after the first read. A fresh scan would pick this up.
    Files.writeString(repo.resolve("after.txt"), "after");

    // Same instant, no TTL elapsed.
    GitStatusService.RepoStatus second = service.status(repo).orElseThrow();

    assertThat(second.dirtyPaths()).isEmpty();
    assertThat(second).isSameAs(first);
  }

  @Test
  void cacheExpiresAfterTtlAndRescans() throws Exception {
    Path repo = initRepo();
    var clock = mutableClock();
    var service = newService(git(true, null, 60), clock);

    GitStatusService.RepoStatus first = service.status(repo).orElseThrow();
    assertThat(first.dirtyPaths()).isEmpty();

    Files.writeString(repo.resolve("after.txt"), "after");
    clock.advance(Duration.ofSeconds(61));

    GitStatusService.RepoStatus second = service.status(repo).orElseThrow();

    assertThat(second.dirtyPaths()).contains("after.txt");
  }

  @Test
  void invalidateForcesRescanWithinTtl() throws Exception {
    // Gap G6 / invariant I5: a pending write that flushes mid-request must be visible
    // before the cache TTL elapses. {@code invalidate()} drops every cached snapshot so the
    // very next {@link GitStatusService#status(Path)} call walks git again.
    Path repo = initRepo();
    var clock = mutableClock();
    var service = newService(git(true, null, 3600), clock);

    GitStatusService.RepoStatus first = service.status(repo).orElseThrow();
    assertThat(first.dirtyPaths()).isEmpty();
    assertThat(first).isSameAs(service.status(repo).orElseThrow());

    Files.writeString(repo.resolve("after.txt"), "after");

    // Clock hasn't moved — without invalidation the next read would still return the stale
    // snapshot. The invalidate hook restores fresh-status behaviour.
    service.invalidate();

    GitStatusService.RepoStatus second = service.status(repo).orElseThrow();

    assertThat(second.dirtyPaths()).contains("after.txt");
    assertThat(second).isNotSameAs(first);
  }

  @Test
  void usesConfiguredRepositoryDirOverCandidate() throws Exception {
    Path configuredRepo = initRepo();
    Path unrelated = tempDir.resolve("unrelated-" + UUID.randomUUID());
    Files.createDirectories(unrelated);

    var service = newService(git(true, configuredRepo.toString(), 60), mutableClock());

    Optional<GitStatusService.RepoStatus> status = service.status(unrelated);

    assertThat(status).isPresent();
    assertThat(status.get().workTree()).isEqualTo(configuredRepo.toRealPath());
  }

  @Test
  void resolvesRepositoryRootFromNestedSubdirectory() throws Exception {
    Path repo = initRepo();
    Path nested = Files.createDirectories(repo.resolve("sub").resolve("dir"));

    var service = newService(git(true, null, 60), mutableClock());

    Optional<GitStatusService.RepoStatus> status = service.status(nested);

    assertThat(status).isPresent();
    assertThat(status.get().workTree()).isEqualTo(repo.toRealPath());
  }

  @Test
  void classifiesStagedAddAsAdded() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("new.txt"), "new");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("new.txt").call();
    }

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("new.txt", ChangeType.ADDED);
    assertThat(status.changeOf("new.txt")).contains(ChangeType.ADDED);
  }

  @Test
  void classifiesUntrackedFileAsUntracked() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("scratch.txt"), "scratch");

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("scratch.txt", ChangeType.UNTRACKED);
  }

  @Test
  void classifiesTrackedModifyAsModified() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("README.md"), "v2");

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("README.md", ChangeType.MODIFIED);
  }

  @Test
  void classifiesGitRmAsDeleted() throws Exception {
    Path repo = initRepo();
    try (Git git = Git.open(repo.toFile())) {
      git.rm().addFilepattern("README.md").call();
    }

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("README.md", ChangeType.DELETED);
  }

  @Test
  void classifiesDeletedFromDiskAsDeleted() throws Exception {
    Path repo = initRepo();
    Files.deleteIfExists(repo.resolve("README.md"));

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("README.md", ChangeType.DELETED);
  }

  @Test
  void stagedAddThenEditStaysAdded() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("new.txt"), "v1");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("new.txt").call();
    }
    Files.writeString(repo.resolve("new.txt"), "v2");

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.changes()).containsEntry("new.txt", ChangeType.ADDED);
  }

  @Test
  void dirtyPathsEqualChangesKeySet() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("added.txt"), "added");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("added.txt").call();
    }
    Files.writeString(repo.resolve("scratch.txt"), "scratch");

    var service = newService(git(true, null, 60), mutableClock());

    RepoStatus status = service.status(repo).orElseThrow();

    assertThat(status.dirtyPaths()).isEqualTo(status.changes().keySet());
  }

  @Test
  void legacyTwoArgConstructorYieldsEmptyChanges() {
    RepoStatus legacy = new RepoStatus(Path.of("/repo"), Set.of("a.txt", "b.txt"));

    assertThat(legacy.changes()).isEmpty();
    assertThat(legacy.dirtyPaths()).containsExactly("a.txt", "b.txt");
    assertThat(legacy.changeOf("a.txt")).isEmpty();
  }

  private Path initRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo-" + UUID.randomUUID());
    try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
      Files.writeString(repoDir.resolve("README.md"), "v1");
      git.add().addFilepattern(".").call();
      git.commit()
              .setMessage("initial")
              .setAuthor("dsl-builder-test", "test@example.com")
              .setCommitter("dsl-builder-test", "test@example.com")
              .call();
    }
    return repoDir;
  }

  private static GitStatusService newService(DslBuilderProperties.Git git, MutableClock clock) {
    var properties = new DslBuilderProperties(
            Path.of(System.getProperty("java.io.tmpdir"), "dsl-builder-gitstatus-test"),
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            java.util.List.of("clean", "build"),
            java.util.List.of("dsl", "models"),
            "project/templates",
            null,
            null,
            null,
            null,
            git,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    return new GitStatusService(properties, clock);
  }

  private static DslBuilderProperties.Git git(boolean enabled, String repositoryDir,
          int ttlSeconds) {
    return new DslBuilderProperties.Git(enabled, repositoryDir, null, null, null, null, ttlSeconds);
  }

  private static MutableClock mutableClock() {
    return new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }
}
