package cbs.nova.starter.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Deque;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class DryRunLogBuffer {

  private final int maxEventsPerRun;
  private final Deque<DryRunLogEvent> events;

  public void add(@NonNull ILoggingEvent event, @NonNull String runId) {
    if (events.size() >= maxEventsPerRun) {
      events.pollFirst();
    }
    events.offerLast(toEvent(event, runId));
  }

  public @NonNull List<DryRunLogEvent> drain() {
    List<DryRunLogEvent> snapshot = List.copyOf(events);
    events.clear();
    return snapshot;
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
