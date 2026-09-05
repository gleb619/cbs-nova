package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bounded staging area for DSL file writes pending a flush. Entries are evicted automatically once
 * either the configured maximum size or the write-time-to-live is exceeded, so abandoned
 * stage-without-drain flows cannot leak heap indefinitely.
 */
@Component
public class DslFileBuffer {

  private final Cache<String, String> pending;

  /**
   * No-arg constructor retained for legacy direct-instantiation callers (tests that build a buffer
   * without a Spring context). Uses {@link DslProperties} defaults and the system ticker.
   */
  public DslFileBuffer() {
    this(new DslProperties());
  }

  /**
   * Spring-visible constructor. Uses the system ticker for time-based eviction.
   */
  public DslFileBuffer(DslProperties properties) {
    this(properties, Ticker.systemTicker());
  }

  /**
   * Package-private constructor that accepts a {@link Ticker}, so tests can advance time
   * deterministically without sleeping.
   */
  DslFileBuffer(DslProperties properties, Ticker ticker) {
    int maxEntries = Math.max(1, properties.getFileBuffer().getMaxEntries());
    long ttlSeconds = Math.max(1L, properties.getFileBuffer().getExpireAfterWriteSeconds());
    this.pending = Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .ticker(ticker)
            .build();
  }

  public void stage(String relativePath, String content) {
    pending.put(normalize(relativePath), content);
  }

  public String get(String relativePath) {
    return pending.getIfPresent(normalize(relativePath));
  }

  /**
   * Atomically drains every currently pending entry. Concurrency guarantee mirrors the original
   * {@link java.util.concurrent.ConcurrentHashMap} implementation: each entry is removed with a
   * value-comparing CAS ({@code remove(key, value)} on the underlying
   * {@link com.github.benmanes.caffeine.cache.Cache#asMap()} view), so if a concurrent
   * {@link #stage} overwrites the same key between iteration and removal, only the writer that wins
   * the CAS keeps the new value, and the snapshot will not contain a stale entry nor a duplicate.
   */
  public Map<String, String> drain() {
    Map<String, String> snapshot = new HashMap<>();
    pending.asMap().forEach((String key, String value) -> {
      if (pending.asMap().remove(key, value)) {
        snapshot.put(key, value);
      }
    });
    return snapshot;
  }

  /**
   * Approximate number of pending entries. Backed by Caffeine's {@link Cache#estimatedSize()},
   * which can lag briefly behind the exact size during eviction bookkeeping — this is an accepted,
   * documented approximation of the prior {@code ConcurrentHashMap.size()}.
   */
  public int pendingCount() {
    return (int) pending.estimatedSize();
  }

  /**
   * Exposed for tests that need to drive Caffeine's maintenance task synchronously
   * ({@link Cache#cleanUp()}). Not part of the runtime API.
   */
  Cache<String, String> cache() {
    return pending;
  }

  private String normalize(String relativePath) {
    if (relativePath == null) {
      return "";
    }
    String normalized = relativePath.replace('\\', '/')
            .replaceAll("/+", "/")
            .replaceAll("^/+", "");
    if (normalized.contains("..")) {
      throw new IllegalArgumentException("path escapes workspace: " + relativePath);
    }
    return normalized;
  }
}
