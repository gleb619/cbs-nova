package cbs.nova.dsl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class ExecutionTraceCollector {

  private final ThreadLocal<List<String>> trace = new ThreadLocal<>();

  public void start() {
    trace.set(new ArrayList<>());
  }

  public void stop() {
    trace.remove();
  }

  public @NonNull List<String> snapshot() {
    List<String> current = trace.get();
    return current != null ? List.copyOf(current) : List.of();
  }

  public void add(@NonNull String entry) {
    List<String> current = trace.get();
    if (current != null) {
      current.add(entry);
    }
  }
}
