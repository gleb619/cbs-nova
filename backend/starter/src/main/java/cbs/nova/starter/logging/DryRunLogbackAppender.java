package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

/**
 * Logback appender that silently routes log events into the per-run {@link DryRunLogBuffer} while a
 * dry-run context is active.
 *
 * <p>
 * The appender does not accumulate events itself; it looks up the active buffer from the
 * {@link DryRunLogBufferRegistry}. In RUN mode or on Temporal worker nodes there is no registered
 * buffer, so the event is ignored.
 */
public class DryRunLogbackAppender extends AppenderBase<ILoggingEvent> {

  public static final int DEFAULT_MAX_EVENTS_PER_RUN = 1000;

  @Getter
  private final DryRunLoggingContext dryRunLoggingContext;

  @Getter
  private final DryRunLogBufferRegistry bufferRegistry;

  public DryRunLogbackAppender(
          @NonNull DryRunLoggingContext dryRunLoggingContext,
          @NonNull DryRunLogBufferRegistry bufferRegistry) {
    this.dryRunLoggingContext = dryRunLoggingContext;
    this.bufferRegistry = bufferRegistry;
  }

  @Override
  protected void append(ILoggingEvent event) {
    String runId = dryRunLoggingContext.currentRunId();
    if (runId == null) {
      return;
    }
    DryRunLogBuffer buffer = bufferRegistry.get(runId);
    if (buffer == null) {
      return;
    }
    buffer.add(event, runId);
  }
}
