package cbs.nova.starter.cache;

import cbs.nova.dsl.PreviewReport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PreviewResultCache {

  private final ConcurrentHashMap<PreviewCacheKey, PreviewCacheEntry> store = new ConcurrentHashMap<>();
  private final long ttlMs;
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();

  public PreviewResultCache(long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public @Nullable PreviewReport get(@NonNull PreviewCacheKey key) {
    var entry = store.get(key);
    if (entry == null) {
      misses.incrementAndGet();
      return null;
    }
    if (isExpired(entry)) {
      store.remove(key);
      misses.incrementAndGet();
      return null;
    }
    hits.incrementAndGet();
    return entry.report();
  }

  public void put(@NonNull PreviewCacheKey key, @NonNull PreviewReport report) {
    store.put(key, new PreviewCacheEntry(report, System.currentTimeMillis(), ttlMs));
  }

  public void invalidateByDslHash(@NonNull String dslDescriptorHash) {
    store.entrySet()
            .removeIf(entry -> entry.getKey().dslDescriptorHash().equals(dslDescriptorHash));
  }

  public void clear() {
    store.clear();
  }

  public @NonNull Map<String, Long> getStats() {
    return Map.of(
            "hits", hits.get(),
            "misses", misses.get());
  }

  long hits() {
    return hits.get();
  }

  long misses() {
    return misses.get();
  }

  private boolean isExpired(@NonNull PreviewCacheEntry entry) {
    return System.currentTimeMillis() - entry.timestamp() > entry.ttlMs();
  }
}
