package cbs.nova.dsl.builder.service;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileBuffer {

  private final Cache<String, String> pending;

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
