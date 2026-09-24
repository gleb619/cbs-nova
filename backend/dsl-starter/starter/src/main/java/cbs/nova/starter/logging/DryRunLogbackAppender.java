package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class DryRunLogbackAppender extends AppenderBase<ILoggingEvent> {

  @Getter
  private final @NonNull DryRunLoggingContext dryRunLoggingContext;

  @Getter
  private final @NonNull DryRunLogBufferRegistry bufferRegistry;

  @Getter
  private final @Nullable DryRunLogEventPublisher publisher;

  //TODO: replace ctor with lomboks one
  @Deprecated(forRemoval = true)
  public DryRunLogbackAppender(
          @NonNull DryRunLoggingContext dryRunLoggingContext,
          @NonNull DryRunLogBufferRegistry bufferRegistry) {
    this(dryRunLoggingContext, bufferRegistry, null);
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
