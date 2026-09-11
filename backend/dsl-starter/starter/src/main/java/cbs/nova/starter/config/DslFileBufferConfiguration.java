package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.service.DslFileBuffer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DslFileBufferConfiguration {

  @Bean
  public DslFileBuffer dslFileBuffer(DslProperties properties) {
    return new DslFileBuffer(pendingCache(properties, Ticker.systemTicker()));
  }

  /**
   * Builds the bounded pending-write cache. Sizing and TTL are clamped to at least one entry / one
   * second so a zero or negative configuration cannot disable eviction or the size bound. Static so
   * tests can build a buffer with a custom {@link Ticker} without a Spring context.
   */
  public static Cache<String, String> pendingCache(DslProperties properties, Ticker ticker) {
    int maxEntries = Math.max(1, properties.fileBuffer().maxEntries());
    long ttlSeconds = Math.max(1L, properties.fileBuffer().expireAfterWriteSeconds());
    return Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .ticker(ticker)
            .build();
  }

  public static Cache<String, String> pendingCache(DslProperties properties) {
    return pendingCache(properties, Ticker.systemTicker());
  }
}
