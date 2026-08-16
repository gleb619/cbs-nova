package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionTraceCollector {

  private final ConcurrentLinkedQueue<String> entries = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean active = new AtomicBoolean();

  public ExecutionTraceCollector() {
  }

  public void start() {
    entries.clear();
    active.set(true);
  }

  public void add(@NonNull String entry) {
    if (active.get()) {
      entries.offer(entry);
    }
  }

  public @NonNull List<String> snapshot() {
    return List.copyOf(entries);
  }

  public void stop() {
    active.set(false);
    entries.clear();
  }
}
