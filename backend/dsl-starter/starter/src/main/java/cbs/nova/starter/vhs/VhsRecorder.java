package cbs.nova.starter.vhs;

import cbs.nova.dsl.Result;
import cbs.nova.starter.core.event.DslExecutionEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionListener;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Translates existing DSL execution events into VHS tape lines and writes them through a
 * {@link VhsTapeSink}.
 *
 * <p>
 * The recorder registers as a {@link DslExecutionListener} on the existing
 * {@link cbs.nova.starter.core.listener.DslExecutionEventBus}; it does not introduce a second
 * capture path. Recording is gated by route matching: a run is only taped when its construct name
 * matches one of the configured {@code cbs.vhs.recordableRoutes} (or the {@code *} wildcard).
 */
@Slf4j
public final class VhsRecorder implements DslExecutionListener {

  private static final String SCHEMA_VERSION = "1";

  private final VhsTapeSink sink;
  private final List<String> recordableRoutes;
  private final ConcurrentHashMap<String, RunState> states = new ConcurrentHashMap<>();

  public VhsRecorder(@NonNull VhsTapeSink sink, @NonNull List<String> recordableRoutes) {
    this.sink = sink;
    this.recordableRoutes = List.copyOf(recordableRoutes);
  }

  @Override
  public void onEvent(@NonNull DslExecutionEvent event) {
    switch (event) {
      case DslRunStartedEvent started -> onRunStarted(started);
      case DslExternalCallEvent call -> onExternalCall(call);
      case DslRunCompletedEvent completed -> onRunCompleted(completed);
    }
  }

  private void onRunStarted(DslRunStartedEvent event) {
    String route = event.name();
    if (!isRecordable(route)) {
      return;
    }
    Instant now = Instant.now();
    RunState state = new RunState(route, event.correlationId(), now);
    states.put(event.runId(), state);
    sink.start(event.runId(), route, event.correlationId());
    TapeEvent tapeEvent = new TapeEvent(
            SCHEMA_VERSION,
            state.nextIndex(),
            "run_started",
            format(now),
            0L,
            null,
            null,
            null,
            new TapeEvent.Timing(format(now), null, null),
            event.correlationId(),
            metadataWithCid(event.correlationId()));
    sink.append(event.runId(), tapeEvent);
  }

  private void onExternalCall(DslExternalCallEvent event) {
    String runId = event.runId();
    if (runId == null) {
      return;
    }
    RunState state = states.get(runId);
    if (state == null) {
      return;
    }
    Instant now = Instant.now();
    String callId = "call_" + String.format("%03d", state.callStarts.size() + 1);
    TapeEvent startEvent = new TapeEvent(
            SCHEMA_VERSION,
            state.nextIndex(),
            "call_start",
            format(now),
            state.relativeMillis(now),
            new TapeEvent.CallMetadata(callId, event.type(), event.target(), event.operation()),
            event.payload(),
            null,
            new TapeEvent.Timing(format(now), null, null),
            state.correlationId,
            Map.of());
    state.callStarts.add(new CallStart(callId, now, startEvent));
    sink.append(runId, startEvent);
  }

  private void onRunCompleted(DslRunCompletedEvent event) {
    RunState state = states.remove(event.runId());
    if (state == null) {
      return;
    }
    Instant now = Instant.now();
    for (CallStart callStart : state.callStarts) {
      TapeEvent endEvent = new TapeEvent(
              SCHEMA_VERSION,
              state.nextIndex(),
              "call_end",
              format(now),
              state.relativeMillis(now),
              callStart.startEvent.callMetadata(),
              null,
              null,
              new TapeEvent.Timing(
                      callStart.startEvent.timing().startedAt(),
                      format(now),
                      state.relativeMillis(now) - state.relativeMillis(callStart.startedAt)),
              state.correlationId,
              Map.of());
      sink.append(event.runId(), endEvent);
    }

    Object output = resultOutput(event.result());
    TapeEvent completedEvent = new TapeEvent(
            SCHEMA_VERSION,
            state.nextIndex(),
            "run_completed",
            format(now),
            state.relativeMillis(now),
            null,
            null,
            output,
            new TapeEvent.Timing(
                    format(state.startInstant),
                    format(now),
                    state.relativeMillis(now)),
            state.correlationId,
            Map.of());
    sink.append(event.runId(), completedEvent);
    long count = sink.close(event.runId());
    log.debug("Closed VHS tape for runId={}: {} events", event.runId(), count);
  }

  private boolean isRecordable(String route) {
    if (recordableRoutes.isEmpty()) {
      return false;
    }
    for (String pattern : recordableRoutes) {
      if ("*".equals(pattern) || pattern.equals(route)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Object> metadataWithCid(@Nullable String correlationId) {
    if (correlationId == null) {
      return Map.of();
    }
    return Map.of("cid", correlationId);
  }

  private static Object resultOutput(@Nullable Result<?> result) {
    if (result == null) {
      return null;
    }
    if (result.isSuccess()) {
      return result.value();
    }
    Throwable cause = result.cause();
    return cause != null
            ? Map.of("error", cause.getClass().getName(), "message", cause.getMessage())
            : null;
  }

  private static String format(Instant instant) {
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }

  private static final class RunState {

    final String route;
    final @Nullable String correlationId;
    final Instant startInstant;
    final List<CallStart> callStarts = new ArrayList<>();
    private int nextIndex;

    RunState(String route, @Nullable String correlationId, Instant startInstant) {
      this.route = route;
      this.correlationId = correlationId;
      this.startInstant = startInstant;
    }

    int nextIndex() {
      return nextIndex++;
    }

    long relativeMillis(Instant instant) {
      return startInstant.until(instant, ChronoUnit.MILLIS);
    }
  }

  private record CallStart(String callId, Instant startedAt, TapeEvent startEvent) {
  }
}
