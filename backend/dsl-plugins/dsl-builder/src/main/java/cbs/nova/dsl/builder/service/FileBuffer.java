package cbs.nova.dsl.builder.service;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileBuffer {

  private final Cache<String, String> pending;
  private final AtomicLong version = new AtomicLong();

  public void stage(String relativePath, String content) {
    pending.put(normalize(relativePath), content);
    version.incrementAndGet();
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
    if (!snapshot.isEmpty()) {
      version.incrementAndGet();
    }
    return snapshot;
  }

  public int pendingCount() {
    return (int) pending.estimatedSize();
  }

  public List<String> stagedPaths() {
    return List.copyOf(pending.asMap().keySet());
  }

  public long version() {
    return version.get();
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
