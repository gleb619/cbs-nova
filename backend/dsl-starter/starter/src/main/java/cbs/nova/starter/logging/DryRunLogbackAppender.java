package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class DryRunLogbackAppender extends AppenderBase<ILoggingEvent> {

  @Getter
  private final DryRunLoggingContext dryRunLoggingContext;

  @Getter
  private final DryRunLogBufferRegistry bufferRegistry;

  @Getter
  private final @Nullable DryRunLogEventPublisher publisher;

  public DryRunLogbackAppender(
          @NonNull DryRunLoggingContext dryRunLoggingContext,
          @NonNull DryRunLogBufferRegistry bufferRegistry) {
    this(dryRunLoggingContext, bufferRegistry, null);
  }

  public DryRunLogbackAppender(
          @NonNull DryRunLoggingContext dryRunLoggingContext,
          @NonNull DryRunLogBufferRegistry bufferRegistry,
          @Nullable DryRunLogEventPublisher publisher) {
    this.dryRunLoggingContext = dryRunLoggingContext;
    this.bufferRegistry = bufferRegistry;
    this.publisher = publisher;
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
    DryRunLogEvent logEvent = buffer.add(event, runId);
    if (publisher != null) {
      publisher.publish(runId, logEvent);
    }
  }
}
