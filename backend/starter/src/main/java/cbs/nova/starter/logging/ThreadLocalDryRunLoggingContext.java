package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
