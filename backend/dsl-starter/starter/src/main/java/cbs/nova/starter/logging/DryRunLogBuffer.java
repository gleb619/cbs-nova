package cbs.nova.starter.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DryRunLogBuffer {

  private final int maxEventsPerRun;
  private final Deque<Entry> events;
  private final AtomicLong nextSequence = new AtomicLong();

  public @NonNull DryRunLogEvent add(@NonNull ILoggingEvent event, @NonNull String runId) {
    if (events.size() >= maxEventsPerRun) {
      events.pollFirst();
    }
    DryRunLogEvent logEvent = toEvent(event, runId);
    long sequence = nextSequence.incrementAndGet();
    events.offerLast(new Entry(sequence, logEvent));
    return logEvent;
  }

  public @NonNull List<DryRunLogEvent> drain() {
    List<DryRunLogEvent> snapshot = new ArrayList<>(events.size());
    for (Entry e : events) {
      snapshot.add(e.event());
    }
    events.clear();
    return snapshot;
  }

  /**
   * Returns buffered entries with their monotonically increasing per-run sequence id, oldest first,
   * capped at {@code limit} (the newest {@code limit} entries are returned when the buffer holds
   * more than {@code limit}). Used by the dry-run log SSE {@code Last-Event-ID} backfill: a
   * reconnecting client with id {@code n} replays every entry whose id is greater than {@code n}.
   *
   * <p>
   * The id space is per-buffer (= per-run, in-memory) — different runs and different JVMs hold
   * independent counters, so a {@code Last-Event-ID} only resolves against the same buffer
   * instance.
   */
  public @NonNull List<Entry> snapshot(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }
    int size = events.size();
    int fromIndex = Math.max(0, size - limit);
    List<Entry> out = new ArrayList<>(Math.min(limit, size));
    int i = 0;
    for (Entry e : events) {
      if (i++ < fromIndex) {
        continue;
      }
      out.add(e);
    }
    return out;
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

  public record Entry(long id, @NonNull DryRunLogEvent event) {
  }
}
