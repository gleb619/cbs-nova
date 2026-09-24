package cbs.nova.starter.builder;

import static cbs.nova.starter.core.StarterConstants.CACHE_DRAFTS_PAGE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_DRAFT_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_EXISTS_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILES_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_HISTORY_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_PENDING_COUNT_KEY;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_STATUS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.dsl.vcs.RepoStatus;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuilderCacheTest {

  private final com.github.benmanes.caffeine.cache.Cache<String, Object> backing = Caffeine
          .newBuilder().build();

  private final BuilderCache cache = new BuilderCache(backing);

  @Test
  void invalidateDraftDropsDraftHistoryAndPagesForOneName() {
    // Seed entries tied to {@code foo} and unrelated entries tied to {@code bar}.
    cache.readDraft("foo", () -> placeholder("foo"));
    cache.history("foo", () -> java.util.List.of());
    cache.readDraft("bar", () -> placeholder("bar"));
    cache.history("bar", () -> java.util.List.of());

    cache.invalidateDraft("foo");

    assertThat(backing.asMap().keySet())
            .extracting(key -> key.toString())
            .containsExactlyInAnyOrder(
                    CACHE_DRAFT_PREFIX + "bar",
                    CACHE_HISTORY_PREFIX + "bar");
  }

  @Test
  void invalidateDraftClearsAllDraftsPageEntries() {
    // A draft page is a paged list across every name; any per-name mutation makes the page
    // stale. {@code invalidateDraft} drops every page entry (the page key is the same
    // regardless of which draft was mutated).
    cache.listDrafts(10, 0, () -> null);
    cache.listDrafts(50, 0, () -> null);
    cache.readDraft("foo", () -> placeholder("foo"));

    cache.invalidateDraft("foo");

    assertThat(backing.asMap().keySet())
            .noneMatch(key -> key.toString().startsWith(CACHE_DRAFTS_PAGE_PREFIX));
    // The per-draft entry is also gone — the per-name draft and history entries share the
    // invalidation hook.
    assertThat(backing.asMap().keySet())
            .noneMatch(key -> key.toString().startsWith(CACHE_DRAFT_PREFIX + "foo"));
  }

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
    cache.readDraft("foo", () -> placeholder("foo"));
    cache.pendingCount(() -> 3);
    cache.vcsStatus(
            () -> Optional.of(RepoStatus.fromDirtyPaths(Path.of("/repo"), java.util.Set.of())));

    cache.clear();

    assertThat(backing.asMap()).isEmpty();
  }

  private static DraftRequest placeholder(String name) {
    return new DraftRequest(name, "process", "Draft", "1", "q", null, null);
  }
}
