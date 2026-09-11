package cbs.nova.starter.builder;

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

  private static final String HISTORY_PREFIX = StarterConstants.CACHE_HISTORY_PREFIX;
  private static final String HISTORY_ENTRY_PREFIX = StarterConstants.CACHE_HISTORY_ENTRY_PREFIX;
  private static final String HISTORY_DIFF_PREFIX = StarterConstants.CACHE_HISTORY_DIFF_PREFIX;
  private static final String DRAFT_PREFIX = StarterConstants.CACHE_DRAFT_PREFIX;
  private static final String DRAFTS_PAGE_PREFIX = StarterConstants.CACHE_DRAFTS_PAGE_PREFIX;
  private static final String EXPORT_PREFIX = StarterConstants.CACHE_EXPORT_PREFIX;
  private static final String FILES_PREFIX = StarterConstants.CACHE_FILES_PREFIX;
  private static final String FILE_PREFIX = StarterConstants.CACHE_FILE_PREFIX;
  private static final String FILE_EXISTS_PREFIX = StarterConstants.CACHE_FILE_EXISTS_PREFIX;
  private static final String PENDING_COUNT_KEY = StarterConstants.CACHE_PENDING_COUNT_KEY;
  private static final String VCS_STATUS_KEY = StarterConstants.CACHE_VCS_STATUS_KEY;

  private final Cache<String, Object> cache;

  public List<DefinitionHistoryEntry> history(String name,
          Supplier<List<DefinitionHistoryEntry>> loader) {
    return get(HISTORY_PREFIX + name, loader);
  }

  public DraftRequest historyEntry(String name, String timestamp, Supplier<DraftRequest> loader) {
    return get(HISTORY_ENTRY_PREFIX + name + ":" + timestamp, loader);
  }

  public HistoryDiffResponse historyDiff(String name, String timestamp,
          Supplier<HistoryDiffResponse> loader) {
    return get(HISTORY_DIFF_PREFIX + name + ":" + timestamp, loader);
  }

  public DraftRequest readDraft(String name, Supplier<DraftRequest> loader) {
    return get(DRAFT_PREFIX + name, loader);
  }

  public PageResponse<DraftSummary> listDrafts(int limit, int offset,
          Supplier<PageResponse<DraftSummary>> loader) {
    return get(DRAFTS_PAGE_PREFIX + limit + ":" + offset, loader);
  }

  public DefinitionBundle exportBundle(boolean includeDrafts, Supplier<DefinitionBundle> loader) {
    return get(EXPORT_PREFIX + includeDrafts, loader);
  }

  public List<FileEntry> listFiles(String prefix, Supplier<List<FileEntry>> loader) {
    return get(FILES_PREFIX + (prefix == null ? "" : prefix), loader);
  }

  public FileContentResponse readFile(String path, Supplier<FileContentResponse> loader) {
    return get(FILE_PREFIX + path, loader);
  }

  public boolean fileExists(String path, Supplier<Boolean> loader) {
    return get(FILE_EXISTS_PREFIX + path, loader);
  }

  public int pendingCount(Supplier<Integer> loader) {
    return get(PENDING_COUNT_KEY, loader);
  }

  public Optional<RepoStatus> vcsStatus(Supplier<Optional<RepoStatus>> loader) {
    return get(VCS_STATUS_KEY, loader);
  }

  public void invalidateDraft(String name) {
    cache.asMap().keySet().removeIf(key -> key.startsWith(DRAFT_PREFIX + name)
            || key.startsWith(HISTORY_PREFIX + name)
            || key.startsWith(HISTORY_ENTRY_PREFIX + name)
            || key.startsWith(HISTORY_DIFF_PREFIX + name));
  }

  public void invalidateFile(String path) {
    cache.asMap().keySet().removeIf(key -> key.startsWith(FILE_PREFIX + path)
            || key.startsWith(FILE_EXISTS_PREFIX + path));
  }

  public void invalidateFiles() {
    invalidatePrefix(FILES_PREFIX);
    invalidatePrefix(FILE_PREFIX);
    invalidatePrefix(FILE_EXISTS_PREFIX);
    cache.invalidate(PENDING_COUNT_KEY);
  }

  public void invalidateVcsStatus() {
    cache.invalidate(VCS_STATUS_KEY);
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
