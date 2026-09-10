package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Default {@link DryRunLoggingContext} backed by SLF4J's {@link MDC}.
 *
 * <p>
 * Why MDC: the Temporal {@link DryRunLoggingContextPropagator} already populates {@link MDC} under
 * {@link DryRunLoggingContext#RUN_ID_HEADER} on {@code setCurrentContext}, and the dry-run logback
 * pattern routes per-run traffic through the same key. Storing the run id in MDC therefore reuses
 * the storage the propagator and the appender are already touching — no separate per-thread slot to
 * keep in sync, and no db/redis round-trip needed for in-process routing. This is the resolution of
 * the "thread local is a bad idea with temporal" TODO that previously lived on
 * {@link ThreadLocalDryRunLoggingContext}: MDC integrates with the Temporal context propagator and
 * the logback pattern, so the ThreadLocal impl is no longer needed on the default path (it is
 * retained as a test double / explicit opt-in via
 * {@code cbs.nova.dryRun.context.type=threadlocal}).
 *
 * <p>
 * Note: MDC is still thread-local by nature. Cross-thread propagation continues to rely on the
 * Temporal {@code ContextPropagator}. That contract is unchanged.
 */
public final class MdcDryRunLoggingContext implements DryRunLoggingContext {

  @Override
  public void setRunId(@Nullable String runId) {
    if (runId == null) {
      MDC.remove(DryRunLoggingContext.RUN_ID_HEADER);
    } else {
      MDC.put(DryRunLoggingContext.RUN_ID_HEADER, runId);
    }
  }

  @Override
  public void clearRunId() {
    MDC.remove(DryRunLoggingContext.RUN_ID_HEADER);
  }

  @Override
  public @Nullable String currentRunId() {
    return MDC.get(DryRunLoggingContext.RUN_ID_HEADER);
  }
}
