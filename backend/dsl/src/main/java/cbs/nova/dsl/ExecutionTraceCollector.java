package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecutionTraceCollector {

  private final ConcurrentHashMap<String, List<String>> traces = new ConcurrentHashMap<>();

  public ExecutionTraceCollector() {
  }

  public void start(@NonNull String runId) {
    traces.put(runId, Collections.synchronizedList(new ArrayList<>()));
  }

  public void stop(@NonNull String runId) {
    traces.remove(runId);
  }

  public @NonNull List<String> snapshot(@NonNull String runId) {
    List<String> current = traces.get(runId);
    if (current == null) {
      return List.of();
    }
    synchronized (current) {
      return List.copyOf(current);
    }
  }

  public void add(@NonNull String runId, @NonNull String entry) {
    List<String> current = traces.get(runId);
    if (current != null) {
      current.add(entry);
    }
  }
}
