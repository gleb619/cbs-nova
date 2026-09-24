package cbs.nova.starter.builder;

import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_EXISTS_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILES_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_PENDING_COUNT_KEY;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_STATUS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.model.VcsModels.LogEntry;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuilderCacheTest {

  private final com.github.benmanes.caffeine.cache.Cache<String, Object> backing = Caffeine
          .newBuilder().build();

  private final BuilderCache cache = new BuilderCache(backing);

  @Test
  void invalidateFileDropsFileAndExistenceEntry() {
    cache.readFile("dsl/A.java", () -> null);
    cache.fileExists("dsl/A.java", () -> true);

    cache.invalidateFile("dsl/A.java");

    assertThat(backing.asMap().keySet())
            .noneMatch(key -> key.toString().startsWith(CACHE_FILE_PREFIX + "dsl/A.java")
                    || key.toString().startsWith(CACHE_FILE_EXISTS_PREFIX + "dsl/A.java"));
  }

  @Test
  void invalidateFilesDropsEveryFileEntryAndPendingCount() {
    cache.listFiles(null, () -> java.util.List.of());
    cache.readFile("dsl/A.java", () -> null);
    cache.fileExists("dsl/B.java", () -> true);
    cache.pendingCount(() -> 3);

    cache.invalidateFiles();

    assertThat(backing.asMap().keySet())
            .noneMatch(key -> {
              String s = key.toString();
              return s.startsWith(CACHE_FILES_PREFIX)
                      || s.startsWith(CACHE_FILE_PREFIX)
                      || s.startsWith(CACHE_FILE_EXISTS_PREFIX)
                      || s.equals(CACHE_PENDING_COUNT_KEY);
            });
  }

  @Test
  void invalidateVcsStatusDropsOnlyStatusEntry() {
    cache.vcsStatus(
            () -> Optional.of(RepoStatus.fromDirtyPaths(Path.of("/repo"), java.util.Set.of())));
    cache.pendingCount(() -> 7);

    cache.invalidateVcsStatus();

    assertThat(backing.asMap().keySet())
            .containsExactly(CACHE_PENDING_COUNT_KEY);
  }

  @Test
  void invalidatePendingCountDropsOnlyCountEntry() {
    cache.vcsStatus(
            () -> Optional.of(RepoStatus.fromDirtyPaths(Path.of("/repo"), java.util.Set.of())));
    cache.pendingCount(() -> 7);

    cache.invalidatePendingCount();

    assertThat(backing.asMap().keySet())
            .containsExactly(CACHE_VCS_STATUS_KEY);
  }

  @Test
  void clearDropsEverything() {
    cache.vcsStatus(
            () -> Optional.of(RepoStatus.fromDirtyPaths(Path.of("/repo"), java.util.Set.of())));
    cache.pendingCount(() -> 3);
    cache.readFile("dsl/A.java", () -> null);

    cache.clear();

    assertThat(backing.asMap()).isEmpty();
  }

  @Test
  void vcsLogRoundTrip() {
    LogEntry entry = new LogEntry("abc123", 1L, "alice", "Publish foo");
    java.util.List<LogEntry> first = cache.vcsLog("dsl/A.java", 20,
            () -> java.util.List.of(entry));
    java.util.List<LogEntry> second = cache.vcsLog("dsl/A.java", 20,
            () -> {
              throw new AssertionError("loader should not be invoked on cache hit");
            });

    assertThat(first).containsExactly(entry);
    assertThat(second).isSameAs(first);
  }

  @Test
  void vcsShowRoundTrip() {
    String first = cache.vcsShow("dsl/A.java", "abc123", () -> "class A {}");
    String second = cache.vcsShow("dsl/A.java", "abc123",
            () -> {
              throw new AssertionError("loader should not be invoked on cache hit");
            });

    assertThat(first).isEqualTo("class A {}");
    assertThat(second).isEqualTo("class A {}");
  }

  @Test
  void invalidateVcsLogDropsOnlyLogEntries() {
    cache.vcsLog("dsl/A.java", 20, () -> java.util.List.of());
    cache.vcsLog("dsl/B.java", 20, () -> java.util.List.of());
    cache.vcsShow("dsl/A.java", "abc123", () -> "class A {}");
    cache.pendingCount(() -> 7);

    cache.invalidateVcsLog();

    assertThat(backing.asMap().keySet())
            .noneMatch(key -> key.toString().startsWith("dsl:vcs-log:"));
    assertThat(backing.asMap().keySet())
            .contains(CACHE_PENDING_COUNT_KEY);
  }
}
