package cbs.nova.starter.interceptors;

import cbs.nova.dsl.ExecutionTreeCollector;
import org.jspecify.annotations.NonNull;

/**
 * Attaches a shared {@link ExecutionTreeCollector} to a run via the {@link RunInterceptor}
 * lifecycle. The same collector instance can be reused across many DSL calls because it scopes all
 * state by runId.
 */
public final class ExecutionTreeCollectorInterceptor implements RunInterceptor {

  private final ExecutionTreeCollector collector;

  public ExecutionTreeCollectorInterceptor(@NonNull ExecutionTreeCollector collector) {
    this.collector = collector;
  }

  @Override
  public void beforeRun(@NonNull String runId) {
    collector.startRun(runId);
  }

  @Override
  public void afterRun(@NonNull String runId) {
    collector.finishRun(runId);
  }
}
