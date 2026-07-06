package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionTraceCollector {
  private static final ThreadLocal<List<String>> TRACE = new ThreadLocal<>();

  public static void start() {
    TRACE.set(new ArrayList<>());
  }

  public static void stop() {
    TRACE.remove();
  }

  public static @NonNull List<String> snapshot() {
    List<String> trace = TRACE.get();
    return trace != null ? List.copyOf(trace) : List.of();
  }

  public static void add(@NonNull String entry) {
    List<String> trace = TRACE.get();
    if (trace != null) {
      trace.add(entry);
    }
  }
}
