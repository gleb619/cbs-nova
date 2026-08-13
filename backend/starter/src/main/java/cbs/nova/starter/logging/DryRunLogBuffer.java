package cbs.nova.starter.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Per-run bounded buffer for dry-run log events.
 *
 * <p>
 * A single instance is created by {@link cbs.nova.starter.core.stage.DryRunLogStage} for each
 * preview/explain run, registered in {@link DryRunLogBufferRegistry} so the global
 * {@link DryRunLogbackAppender} can route events into it, and then drained and removed when the run
 * completes.
 */
public final class DryRunLogBuffer {

  private final int maxEventsPerRun;
  private final List<DryRunLogEvent> events;

  public DryRunLogBuffer(int maxEventsPerRun) {
    this.maxEventsPerRun = maxEventsPerRun;
    this.events = Collections.synchronizedList(new ArrayList<>(maxEventsPerRun));
  }

  /**
   * Convert the given Logback event to a typed dry-run event and store it in this buffer.
   *
   * <p>
   * If the buffer is already full, the oldest event is dropped before the new one is added.
   */
  public void add(@NonNull ILoggingEvent event, @NonNull String runId) {
    synchronized (events) {
      if (events.size() >= maxEventsPerRun) {
        events.removeFirst();
      }
      events.add(toEvent(event, runId));
    }
  }

  /**
   * Return all captured events and clear the buffer.
   */
  public @NonNull List<DryRunLogEvent> drain() {
    synchronized (events) {
      List<DryRunLogEvent> snapshot = List.copyOf(events);
      events.clear();
      return snapshot;
    }
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
