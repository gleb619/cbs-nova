package cbs.nova.starter.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DryRunLogBuffer {

  private final int maxEventsPerRun;
  private final List<DryRunLogEvent> events;

  public DryRunLogBuffer(int maxEventsPerRun) {
    this.maxEventsPerRun = maxEventsPerRun;
    this.events = Collections.synchronizedList(new ArrayList<>(maxEventsPerRun));
  }

  public void add(@NonNull ILoggingEvent event, @NonNull String runId) {
    synchronized (events) {
      if (events.size() >= maxEventsPerRun) {
        events.removeFirst();
      }
      events.add(toEvent(event, runId));
    }
  }

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
