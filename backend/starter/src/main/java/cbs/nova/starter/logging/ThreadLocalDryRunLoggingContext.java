package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link DryRunLoggingContext} implementation based on a {@link ThreadLocal}.
 *
 * <p>
 * This is the default implementation in the Spring Boot starter. It supports explicit
 * {@link #setRunId(String)}/{@link #clearRunId()} calls, which makes it suitable for cross-node
 * propagation (e.g. Temporal workers) that restore the runId from an RPC header before executing
 * activity/workflow code.
 */
public final class ThreadLocalDryRunLoggingContext implements DryRunLoggingContext {

  private final ThreadLocal<String> runId = new ThreadLocal<>();

  @Override
  public void setRunId(@Nullable String runId) {
    if (runId == null) {
      this.runId.remove();
    } else {
      this.runId.set(runId);
    }
  }

  @Override
  public void clearRunId() {
    this.runId.remove();
  }

  @Override
  public @Nullable String currentRunId() {
    return this.runId.get();
  }
}
