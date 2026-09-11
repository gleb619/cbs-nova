package cbs.nova.starter.service;

import lombok.AllArgsConstructor;

import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
public class PreviewResultCache {

  private final Cache<PreviewCacheKey, PreviewReport> cache;

  public @Nullable PreviewReport get(@NonNull PreviewCacheKey key) {
    return cache.getIfPresent(key);
  }

  public void put(@NonNull PreviewCacheKey key, @NonNull PreviewReport report) {
    cache.put(key, report);
  }

  /**
   * Drops every cached preview whose key carries the given DSL descriptor hash. Retained for future
   * per-construct invalidation (e.g. workbench publish of a single process). Reload of the whole
   * registry currently uses {@link #clear()} instead, since every cached entry is suspect after a
   * global swap.
   */
  public void invalidateByDslHash(@NonNull String dslDescriptorHash) {
    cache.asMap().keySet().removeIf(k -> k.dslDescriptorHash().equals(dslDescriptorHash));
  }

  public void clear() {
    cache.invalidateAll();
  }

  public @NonNull Map<String, Long> getStats() {
    return Map.of(
            "hits", hits(),
            "misses", misses());
  }

  public long hits() {
    return cache.stats().hitCount();
  }

  public long misses() {
    return cache.stats().missCount();
  }
}
