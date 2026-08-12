package cbs.nova.starter.interceptors;

import org.jspecify.annotations.NonNull;

/**
 * Lifecycle interceptor around a single DSL run. Allows cross-cutting concerns such as call-tree
 * collection, external-call recording and metrics to be attached without manual lifecycle in each
 * entry point.
 */
public interface RunInterceptor {

  void beforeRun(@NonNull String runId);

  void afterRun(@NonNull String runId);

  /**
   * Starts the run and returns an {@link AutoCloseable} handle that signals the end of the run.
   * This works for both synchronous {@code try-with-resources} blocks and asynchronous execution
   * models where the handle can be closed in a callback or {@code CompletableFuture#whenComplete}.
   */
  default @NonNull AutoCloseable startRun(@NonNull String runId) {
    beforeRun(runId);
    return () -> afterRun(runId);
  }
}
