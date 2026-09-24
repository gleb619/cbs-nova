package cbs.nova.starter.builder;

import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_EXISTS_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILE_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_FILES_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_PENDING_COUNT_KEY;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_BRANCH_KEY;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_LOG_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_SHOW_PREFIX;
import static cbs.nova.starter.core.StarterConstants.CACHE_VCS_STATUS_KEY;

import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.core.StarterConstants;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BuilderCache {

  private final Cache<String, Object> cache;

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

  public List<LogEntry> vcsLog(String path, int limit, Supplier<List<LogEntry>> loader) {
    return get(CACHE_VCS_LOG_PREFIX + path + ":" + limit, loader);
  }

  public String vcsShow(String path, String commitId, Supplier<String> loader) {
    return get(CACHE_VCS_SHOW_PREFIX + path + ":" + commitId, loader);
  }

  public String vcsBranch(Supplier<String> loader) {
    return get(CACHE_VCS_BRANCH_KEY, loader);
  }

  public void invalidateVcsLog() {
    invalidatePrefix(CACHE_VCS_LOG_PREFIX);
    invalidatePrefix(CACHE_VCS_SHOW_PREFIX);
    cache.invalidate(StarterConstants.CACHE_VCS_BRANCH_KEY);
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
