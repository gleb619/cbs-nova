package cbs.nova.starter.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DslFileBuffer {

  // TODO: redo to caffeine, it can leaad to a memory leak now
  private final ConcurrentHashMap<String, String> pending = new ConcurrentHashMap<>();

  public void stage(String relativePath, String content) {
    pending.put(normalize(relativePath), content);
  }

  public String get(String relativePath) {
    return pending.get(normalize(relativePath));
  }

  public Map<String, String> drain() {
    Map<String, String> snapshot = new HashMap<>();
    pending.forEach((String key, String value) -> {
      if (pending.remove(key, value)) {
        snapshot.put(key, value);
      }
    });
    return snapshot;
  }

  public int pendingCount() {
    return pending.size();
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
