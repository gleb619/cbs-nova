package cbs.nova.starter.cache;

import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
import cbs.nova.starter.service.PreviewResultCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

public final class PreviewResultCacheTestSupport {

  private static final long DEFAULT_MAX_SIZE = 10_000L;

  private PreviewResultCacheTestSupport() {
  }

  public static PreviewResultCache cache(long ttlMs) {
    return cache(ttlMs, DEFAULT_MAX_SIZE);
  }

  public static PreviewResultCache cache(long ttlMs, long maxSize) {
    return new PreviewResultCache(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(ttlMs))
            .maximumSize(maxSize)
            .recordStats()
            .build());
  }
}
