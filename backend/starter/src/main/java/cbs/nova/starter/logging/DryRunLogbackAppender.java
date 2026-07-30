package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logback appender that silently records log events only while a dry-run context is active. Events
 * are stored in a bounded, runId-keyed buffer and can be drained for reporting.
 */
public class DryRunLogbackAppender extends AppenderBase<ILoggingEvent> {

  public static final int DEFAULT_MAX_EVENTS_PER_RUN = 1000;

  @Getter
  private final DryRunLoggingContext dryRunLoggingContext;

  @Getter
  private final int maxEventsPerRun;

  private final ConcurrentHashMap<String, List<DryRunLogEvent>> buffers = new ConcurrentHashMap<>();

  public DryRunLogbackAppender(
          @NonNull DryRunLoggingContext dryRunLoggingContext,
          int maxEventsPerRun) {
    this.dryRunLoggingContext = dryRunLoggingContext;
    this.maxEventsPerRun = maxEventsPerRun;
  }

  @Override
  protected void append(ILoggingEvent event) {
    String runId = dryRunLoggingContext.currentRunId();
    if (runId == null) {
      return;
    }
    buffers.compute(runId, (key, buffer) -> {
      List<DryRunLogEvent> events = buffer != null ? buffer : new ArrayList<>(maxEventsPerRun);
      if (events.size() >= maxEventsPerRun) {
        events.removeFirst();
      }
      events.add(toEvent(event, runId));
      return events;
    });
  }

  /**
   * Returns and removes all captured events for the given runId.
   */
  public List<DryRunLogEvent> drain(@NonNull String runId) {
    List<DryRunLogEvent> removed = buffers.remove(runId);
    return removed != null ? List.copyOf(removed) : List.of();
  }

  private DryRunLogEvent toEvent(ILoggingEvent event, String runId) {
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc == null) {
      mdc = Map.of();
    }
    return new DryRunLogEvent(
            event.getLevel().toString(),
            event.getFormattedMessage(),
            event.getTimeStamp(),
            mdc,
            runId);
  }
}
