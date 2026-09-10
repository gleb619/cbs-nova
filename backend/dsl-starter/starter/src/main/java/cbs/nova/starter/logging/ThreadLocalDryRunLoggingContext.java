package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.jspecify.annotations.Nullable;

/**
 * Thread-local {@link DryRunLoggingContext} retained as a test double and as an explicit opt-in via
 * {@code cbs.nova.dryRun.context.type=threadlocal}. The production default is
 * {@link MdcDryRunLoggingContext}: MDC integrates with the Temporal context propagator and the
 * logback pattern that the dry-run pipeline already uses, so the ThreadLocal slot no longer needs
 * to live on the default bean path.
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
