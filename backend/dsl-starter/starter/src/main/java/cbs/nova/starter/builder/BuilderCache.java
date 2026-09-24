package cbs.nova.starter.builder;

import static cbs.nova.starter.core.StarterConstants.CACHE_DRAFTS_PAGE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_DRAFT_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_EXPORT_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILES_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_EXISTS_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_HISTORY_DIFF_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_HISTORY_ENTRY_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_HISTORY_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_PENDING_COUNT_KEY;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_STATUS_KEY;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BuilderCache {

  private final Cache<String, Object> cache;

  public List<DefinitionHistoryEntry> history(String name,
          Supplier<List<DefinitionHistoryEntry>> loader) {
    return get(CACHE_HISTORY_PREFIX + name, loader);
  }

  public DraftRequest historyEntry(String name, String timestamp, Supplier<DraftRequest> loader) {
    return get(CACHE_HISTORY_ENTRY_PREFIX + name + ":" + timestamp, loader);
  }

  public HistoryDiffResponse historyDiff(String name, String timestamp,
          Supplier<HistoryDiffResponse> loader) {
    return get(CACHE_HISTORY_DIFF_PREFIX + name + ":" + timestamp, loader);
  }

  public DraftRequest readDraft(String name, Supplier<DraftRequest> loader) {
    return get(CACHE_DRAFT_PREFIX + name, loader);
  }

  public PageResponse<DraftSummary> listDrafts(int limit, int offset,
          Supplier<PageResponse<DraftSummary>> loader) {
    return get(CACHE_DRAFTS_PAGE_PREFIX + limit + ":" + offset, loader);
  }

  public DefinitionBundle exportBundle(boolean includeDrafts, Supplier<DefinitionBundle> loader) {
    return get(CACHE_EXPORT_PREFIX + includeDrafts, loader);
  }

  public List<FileEntry> listFiles(String prefix, Supplier<List<FileEntry>> loader) {
    return get(CACHE_FILES_PREFIX + (prefix == null ? "" : prefix), loader);
  }

  public FileContentResponse readFile(String path, Supplier<FileContentResponse> loader) {
    return get(CACHE_FILE_PREFIX + path, loader);
  }

  public boolean fileExists(String path, Supplier<Boolean> loader) {
    return get(CACHE_FILE_EXISTS_PREFIX + path, loader);
  }

  public int pendingCount(Supplier<Integer> loader) {
    return get(CACHE_PENDING_COUNT_KEY, loader);
  }

  public Optional<RepoStatus> vcsStatus(Supplier<Optional<RepoStatus>> loader) {
    return get(CACHE_VCS_STATUS_KEY, loader);
  }

  public void invalidateDraft(String name) {
    cache.asMap().keySet().removeIf(key -> key.startsWith(CACHE_DRAFT_PREFIX + name)
            || key.startsWith(CACHE_HISTORY_PREFIX + name)
            || key.startsWith(CACHE_HISTORY_ENTRY_PREFIX + name)
            || key.startsWith(CACHE_HISTORY_DIFF_PREFIX + name));
    // Drafts page is a paged list across all names; any mutation to one draft makes the page
    // stale. Drop every page entry rather than try to compute a precise invalidation.
    invalidatePrefix(CACHE_DRAFTS_PAGE_PREFIX);
  }

  public void invalidateFile(String path) {
    cache.asMap().keySet().removeIf(key -> key.startsWith(CACHE_FILE_PREFIX + path)
            || key.startsWith(CACHE_FILE_EXISTS_PREFIX + path));
  }

  public void invalidateFiles() {
    invalidatePrefix(CACHE_FILES_PREFIX);
    invalidatePrefix(CACHE_FILE_PREFIX);
    invalidatePrefix(CACHE_FILE_EXISTS_PREFIX);
    cache.invalidate(CACHE_PENDING_COUNT_KEY);
  }

  public void invalidateVcsStatus() {
    cache.invalidate(CACHE_VCS_STATUS_KEY);
  }

  public void invalidatePendingCount() {
    cache.invalidate(CACHE_PENDING_COUNT_KEY);
  }

  public void clear() {
    cache.invalidateAll();
  }

  public long hits() {
    return cache.stats().hitCount();
  }

  public long misses() {
    return cache.stats().missCount();
  }

  @SuppressWarnings("unchecked")
  private <T> T get(String key, Supplier<T> loader) {
    return (T) cache.get(key, k -> loader.get());
  }

  private void invalidatePrefix(String prefix) {
    cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
  }
}
