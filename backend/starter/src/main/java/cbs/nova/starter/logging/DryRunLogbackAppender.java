package cbs.nova.starter.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logback appender that silently records log events only while a dry-run context is active. Events
 * are stored in a bounded, runId-keyed buffer and can be drained for reporting.
 */
public class DryRunLogbackAppender extends AppenderBase<ILoggingEvent> {

  // TODO: replace with contructor param, configure on sprinb config
  private static final int DEFAULT_MAX_EVENTS_PER_RUN = 1000;

  // TODO: replace with some record instead of `List<Map<String, Object>>`, add correspondent
  // methods
  private final ConcurrentHashMap<String, List<Map<String, Object>>> buffers = new ConcurrentHashMap<>();

  // TODO: replace with contructor param, configure on sprinb config
  private int maxEventsPerRun = DEFAULT_MAX_EVENTS_PER_RUN;

  // TODO: remove empty contructor
  public DryRunLogbackAppender() {
  }

  // TODO: replace with lombok
  public DryRunLogbackAppender(int maxEventsPerRun) {
    this.maxEventsPerRun = maxEventsPerRun;
  }

  /** Maximum number of events retained per runId before older entries are dropped. */
  // TODO: remove setter
  public void setMaxEventsPerRun(int maxEventsPerRun) {
    this.maxEventsPerRun = maxEventsPerRun;
  }

  @Override
  protected void append(ILoggingEvent event) {
    // TODO: replace with use of GlobalManager instead
    String runId = DryRunLoggingContext.currentRunId();
    if (runId == null) {
      return;
    }
    buffers.compute(runId, (key, buffer) -> {
      List<Map<String, Object>> events = buffer != null ? buffer : new ArrayList<>(maxEventsPerRun);
      if (events.size() >= maxEventsPerRun) {
        events.removeFirst();
      }
      events.add(toEventMap(event));
      return events;
    });
  }

  /**
   * Returns and removes all captured events for the given runId.
   */
  public List<Map<String, Object>> drain(String runId) {
    List<Map<String, Object>> removed = buffers.remove(runId);
    return removed != null ? List.copyOf(removed) : List.of();
  }

  // TODO: add runId
  private Map<String, Object> toEventMap(ILoggingEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()));
    map.put("level", event.getLevel().toString());
    map.put("logger", event.getLoggerName());
    map.put("message", event.getFormattedMessage());
    return map;
  }
}
