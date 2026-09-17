package cbs.nova.dsl.listener;

import org.jspecify.annotations.NonNull;

import java.util.List;

public final class NoopExecutionTraceCollector extends ExecutionTraceCollector {

  public static final NoopExecutionTraceCollector INSTANCE = new NoopExecutionTraceCollector();

  private NoopExecutionTraceCollector() {
  }

  @Override
  public void start() {
  }

  @Override
  public void add(@NonNull String entry) {
  }

  @Override
  public @NonNull List<String> snapshot() {
    return List.of();
  }

  @Override
  public void stop() {
  }
}
