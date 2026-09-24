package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class DslGitStatusResolverTest {

  @TempDir
  Path tempDir;

  @Test
  void delegatesToBuilderClientWhenAvailable() {
    var expected = Optional.of(new RepoStatus(tempDir, Set.of("builder.txt")));
    var builder = mock(DslBuilderClient.class);
    when(builder.vcsStatus()).thenReturn(expected);

    var resolver = newResolver(dslProperties(true, null, 60), builder);

    assertThat(resolver.status(tempDir)).isSameAs(expected);
  }

  @Test
  void returnsEmptyWhenGitDisabled() {
    var resolver = newResolver(dslProperties(false, null, 60), null);

    assertThat(resolver.status(tempDir)).isEmpty();
  }

  @Test
  void returnsEmptyWhenGitConfigurationIsNull() {
    var props = mock(DslProperties.class);
    when(props.git()).thenReturn(null);
    var resolver = newResolver(props, null);

    assertThat(resolver.status(tempDir)).isEmpty();
  }

  @Test
  void reportsCleanRepository() throws Exception {
    Path repo = initRepo();
    var resolver = newResolver(dslProperties(true, null, 60), null);

    Optional<RepoStatus> status = resolver.status(repo);

    assertThat(status).isPresent();
    assertThat(status.get().dirtyPaths()).isEmpty();
    assertThat(status.get().workTree()).isEqualTo(repo.toRealPath());
  }

  @Test
  void reportsDirtyPathsAcrossAllStatusCategories() throws Exception {
    Path repo = initRepo();

    try (Git git = Git.open(repo.toFile())) {
      Files.writeString(repo.resolve("added.txt"), "added");
      git.add().addFilepattern("added.txt").call();

      Files.writeString(repo.resolve("changedBase.txt"), "v2");
      git.add().addFilepattern("changedBase.txt").call();

      Files.writeString(repo.resolve("modifiedBase.txt"), "v2");

      Files.writeString(repo.resolve("untracked.txt"), "untracked");

      git.rm().addFilepattern("removeBase.txt").call();
    }
    Files.deleteIfExists(repo.resolve("missingBase.txt"));

    var resolver = newResolver(dslProperties(true, null, 60), null);

    RepoStatus status = resolver.status(repo).orElseThrow();

    assertThat(status.dirtyPaths())
            .contains("added.txt", "changedBase.txt", "modifiedBase.txt",
                    "untracked.txt", "removeBase.txt", "missingBase.txt");

    Map<String, ChangeType> changes = status.changes();
    assertThat(changes)
            .containsEntry("added.txt", ChangeType.ADDED)
            .containsEntry("changedBase.txt", ChangeType.MODIFIED)
            .containsEntry("modifiedBase.txt", ChangeType.MODIFIED)
            .containsEntry("untracked.txt", ChangeType.UNTRACKED)
            .containsEntry("removeBase.txt", ChangeType.DELETED)
            .containsEntry("missingBase.txt", ChangeType.DELETED);
    assertThat(status.dirtyPaths()).isEqualTo(changes.keySet());
  }

  @Test
  void cacheHitDoesNotRescanWithinTtl() throws Exception {
    Path repo = initRepo();
    var clock = mutableClock();
    var resolver = newResolver(dslProperties(true, null, 60), null);
    resolver.setClock(clock);

    RepoStatus first = resolver.status(repo).orElseThrow();
    assertThat(first.dirtyPaths()).isEmpty();

    Files.writeString(repo.resolve("after.txt"), "after");

    RepoStatus second = resolver.status(repo).orElseThrow();

    assertThat(second.dirtyPaths()).isEmpty();
    assertThat(second).isSameAs(first);
  }

  @Test
  void cacheExpiresAfterTtlAndRescans() throws Exception {
    Path repo = initRepo();
    var clock = mutableClock();
    var resolver = newResolver(dslProperties(true, null, 60), null);
    resolver.setClock(clock);

    RepoStatus first = resolver.status(repo).orElseThrow();
    assertThat(first.dirtyPaths()).isEmpty();

    Files.writeString(repo.resolve("after.txt"), "after");
    clock.advance(Duration.ofSeconds(61));

    RepoStatus second = resolver.status(repo).orElseThrow();

    assertThat(second.dirtyPaths()).contains("after.txt");
  }

  @Test
  void returnsEmptyWhenCandidateIsNotARepository() throws Exception {
    Path notARepo = tempDir.resolve("not-a-repo-" + UUID.randomUUID());
    Files.createDirectories(notARepo);
    var resolver = newResolver(dslProperties(true, null, 60), null);

    assertThat(resolver.status(notARepo)).isEmpty();
  }

  @Test
  void usesConfiguredRepositoryDirOverCandidate() throws Exception {
    Path configuredRepo = initRepo();
    Path unrelated = tempDir.resolve("unrelated-" + UUID.randomUUID());
    Files.createDirectories(unrelated);

    var resolver = newResolver(dslProperties(true, configuredRepo.toString(), 60), null);

    Optional<RepoStatus> status = resolver.status(unrelated);

    assertThat(status).isPresent();
    assertThat(status.get().workTree()).isEqualTo(configuredRepo.toRealPath());
    assertThat(status.get().dirtyPaths()).isEmpty();
  }

  private Path initRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo-" + UUID.randomUUID());
    try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
      Files.writeString(repoDir.resolve("tracked.md"), "v1");
      Files.writeString(repoDir.resolve("changedBase.txt"), "v1");
      Files.writeString(repoDir.resolve("modifiedBase.txt"), "v1");
      Files.writeString(repoDir.resolve("removeBase.txt"), "v1");
      Files.writeString(repoDir.resolve("missingBase.txt"), "v1");
      git.add().addFilepattern(".").call();
      git.commit()
              .setMessage("initial")
              .setAuthor("dsl-starter-test", "test@example.com")
              .setCommitter("dsl-starter-test", "test@example.com")
              .call();
    }
    return repoDir;
  }

  private static DslGitStatusResolver newResolver(DslProperties props, DslBuilderClient builder) {
    return newResolver(props, builder, Clock.systemUTC());
  }

  private static DslGitStatusResolver newResolver(DslProperties props, DslBuilderClient builder,
          Clock clock) {
    var resolver = new DslGitStatusResolver(props, provider(builder));
    resolver.setClock(clock);
    return resolver;
  }

  private static ObjectProvider<DslBuilderClient> provider(DslBuilderClient client) {
    return new ObjectProvider<>() {
      @Override
      public DslBuilderClient getIfAvailable() {
        return client;
      }

      @Override
      public DslBuilderClient getObject() {
        return client;
      }

      @Override
      public DslBuilderClient getIfUnique() {
        return client;
      }
    };
  }

  private static DslProperties dslProperties(boolean enabled, String repositoryDir,
          int ttlSeconds) {
    return DslProperties.builder()
            .git(DslProperties.Git.builder()
                    .enabled(enabled)
                    .repositoryDir(repositoryDir)
                    .statusCacheTtlSeconds(ttlSeconds)
                    .build())
            .build();
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
