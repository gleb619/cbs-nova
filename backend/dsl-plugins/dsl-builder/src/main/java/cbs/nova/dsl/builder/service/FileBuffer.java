package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileBuffer {

  private final Cache<String, String> pending;

  @Autowired
  public FileBuffer(DslBuilderProperties properties) {
    this(properties, Ticker.systemTicker());
  }

  FileBuffer(DslBuilderProperties properties, Ticker ticker) {
    int maxEntries = Math.max(1, properties.fileBuffer().maxEntries());
    long ttlSeconds = Math.max(1L, properties.fileBuffer().expireAfterWriteSeconds());
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

  public Map<String, String> drain() {
    Map<String, String> snapshot = new HashMap<>();
    pending.asMap().forEach((String key, String value) -> {
      if (pending.asMap().remove(key, value)) {
        snapshot.put(key, value);
      }
    });
    return snapshot;
  }

  public int pendingCount() {
    return (int) pending.estimatedSize();
  }

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
