package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExecutionTraceCollector {

  private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
  private boolean active;

  public ExecutionTraceCollector() {
  }

  public void start() {
    synchronized (entries) {
      active = true;
    }
  }

  public void add(@NonNull String entry) {
    synchronized (entries) {
      if (active) {
        entries.add(entry);
      }
    }
  }

  public @NonNull List<String> snapshot() {
    synchronized (entries) {
      return List.copyOf(entries);
    }
  }

  public void stop() {
    synchronized (entries) {
      active = false;
      entries.clear();
    }
  }
}
