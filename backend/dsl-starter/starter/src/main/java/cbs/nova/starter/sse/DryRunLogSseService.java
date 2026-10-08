package cbs.nova.starter.sse;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.logging.DryRunLogBuffer;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogEvent;
import cbs.nova.starter.logging.DryRunLogEventPublisher;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE fan-out for live dry-run (preview/explain) log lines, keyed by trace/run id. Mirrors
 * {@link ExecutionSseService}: one emitter per id, heartbeat pings, removal on send failure,
 * {@code cbs.sse.max-subscribers} cap. Publishing is a no-op when nobody is subscribed — the
 * {@code DryRunLogBuffer} remains the source of truth for history and replay.
 *
 * <p>
 * Dry-run log ids are per-buffer (= per-run, in-memory). On {@code Last-Event-ID} reconnect the
 * buffer is consulted first: entries whose id is greater than the header are replayed oldest first,
 * then the live cursor continues above the highest replayed id. The id space is local to the buffer
 * instance, so a {@code Last-Event-ID} only resolves on the same JVM that produced the original
 * stream.
 */
@Slf4j
@Service
public final class DryRunLogSseService implements DryRunLogEventPublisher {

  /**
   * See {@link ExecutionSseService#subscribeLock}. Cross-key atomicity requires a shared monitor:
   * {@code ConcurrentHashMap.compute} alone only serializes per-key.
   */
  private final Object subscribeLock = new Object();

  private final Map<String, EmitterState> emitters = new ConcurrentHashMap<>();
  private final AtomicInteger activeCount = new AtomicInteger();
  private final int maxSubscribers;
  private final int backfillMax;
  private final ObjectProvider<DryRunLogBufferRegistry> bufferRegistryProvider;
  private final ScheduledExecutorService heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "dry-run-log-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                  });

  public DryRunLogSseService(
          CbsSseProperties properties,
          ObjectProvider<DryRunLogBufferRegistry> bufferRegistryProvider) {
    this.maxSubscribers = properties.maxSubscribers();
    this.backfillMax = properties.lastEventIdBackfillMax();
    this.bufferRegistryProvider = bufferRegistryProvider;
    heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
            StarterConstants.SSE_HEARTBEAT_SECONDS,
            StarterConstants.SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void publish(@NonNull String runId, @NonNull DryRunLogEvent event) {
    EmitterState state = emitters.get(runId);
    if (state == null) {
      return;
    }
    SseEmitter emitter = state.emitter();
    long eventId = state.nextId().getAndIncrement();
    try {
      emitter.send(SseEmitter.event()
              .id(Long.toString(eventId))
              .name("log")
              .data(event));
    } catch (IOException ex) {
      log.debug("Failed to send dry-run log SSE event for run {}, removing emitter", runId, ex);
      remove(runId, emitter);
    } catch (IllegalStateException ex) {
      log.debug("Emitter for dry-run run {} already complete", runId);
      remove(runId, emitter);
    }
  }

  /**
   * Opens a new SSE stream for {@code runId}. When {@code lastEventId} is positive and a
   * {@link DryRunLogBufferRegistry} is available, the response is preceded by a bounded backfill of
   * every buffer entry for this run whose id is strictly greater than {@code lastEventId}, oldest
   * first; the live cursor is set above the highest replayed id. With no {@code Last-Event-ID} the
   * live cursor starts at 1 and no backfill is emitted.
   */
  public @NonNull SseEmitter subscribe(@NonNull String runId, long lastEventId) {
    EmitterState state;
    EmitterState previous;
    boolean isNewId;
    synchronized (subscribeLock) {
      previous = emitters.get(runId);
      if (previous != null) {
        isNewId = false;
      } else {
        int current = activeCount.get();
        if (current >= maxSubscribers) {
          throw new DslSubscriberCapExceededException(maxSubscribers, runId);
        }
        isNewId = true;
      }
      SseEmitter emitter = new SseEmitter(StarterConstants.SSE_EMITTER_TIMEOUT_MS);
      AtomicLong cursor = new AtomicLong(1L);
      state = new EmitterState(emitter, cursor);
      emitters.put(runId, state);
      if (isNewId) {
        activeCount.incrementAndGet();
      }
    }
    SseEmitter emitter = state.emitter();
    if (previous != null) {
      previous.emitter().complete();
    }
    // Stale-callback protection: capture `state` as a local. Compare-and-delete in remove() so
    // a callback fired after a newer emitter replaced this one will NOT free the newer slot.
    AtomicLong cursor = state.nextId();
    emitter.onCompletion(() -> remove(runId, emitter));
    emitter.onTimeout(() -> remove(runId, emitter));
    emitter.onError((_) -> remove(runId, emitter));
    if (lastEventId > 0) {
      backfillAndAdvance(runId, lastEventId, state);
    }
    try {
      long connectedId = cursor.getAndIncrement();
      emitter.send(SseEmitter.event()
              .id(Long.toString(connectedId))
              .comment("connected"));
    } catch (IOException | IllegalStateException ex) {
      log.debug("Failed to send initial SSE comment for dry-run run {}", runId, ex);
    }
    return emitter;
  }

  public int subscriberCount(@NonNull String runId) {
    return emitters.containsKey(runId) ? 1 : 0;
  }

  public int activeSubscriberCount() {
    return activeCount.get();
  }

  public int maxSubscribers() {
    return maxSubscribers;
  }

  private void remove(@NonNull String runId, @NonNull SseEmitter emitter) {
    synchronized (subscribeLock) {
      EmitterState current = emitters.get(runId);
      if (current != null && current.emitter() == emitter) {
        emitters.remove(runId);
        activeCount.decrementAndGet();
      }
    }
  }

  private void sendHeartbeats() {
    for (Map.Entry<String, EmitterState> entry : emitters.entrySet()) {
      EmitterState state = entry.getValue();
      SseEmitter emitter = state.emitter();
      long pingId = state.nextId().getAndIncrement();
      try {
        emitter.send(SseEmitter.event()
                .id(Long.toString(pingId))
                .comment("ping"));
      } catch (IOException | IllegalStateException ex) {
        log.debug("Heartbeat failed for dry-run run {}, removing emitter", entry.getKey(), ex);
        remove(entry.getKey(), emitter);
      }
    }
  }

  private void backfillAndAdvance(@NonNull String runId, long lastEventId,
          @NonNull EmitterState state) {
    DryRunLogBufferRegistry registry = bufferRegistryProvider.getIfAvailable();
    if (registry == null) {
      return;
    }
    DryRunLogBuffer buffer = registry.get(runId);
    if (buffer == null) {
      return;
    }
    List<DryRunLogBuffer.Entry> entries = buffer.snapshot(backfillMax);
    long maxSeen = lastEventId;
    for (DryRunLogBuffer.Entry entry : entries) {
      if (entry.id() <= lastEventId) {
        continue;
      }
      try {
        state.emitter().send(SseEmitter.event()
                .id(Long.toString(entry.id()))
                .name("log")
                .data(entry.event()));
      } catch (IOException | IllegalStateException ex) {
        log.debug("Backfill send failed for dry-run run {} at id {}", runId, entry.id(), ex);
        remove(runId, state.emitter());
        return;
      }
      if (entry.id() > maxSeen) {
        maxSeen = entry.id();
      }
    }
    state.nextId().set(maxSeen + 1);
  }

  @PreDestroy
  public void destroy() {
    heartbeatExecutor.shutdown();
    try {
      if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        heartbeatExecutor.shutdownNow();
      }
    } catch (InterruptedException ex) {
      heartbeatExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Per-emitter cursor. The {@code nextId} cursor hands out monotonically increasing ids for live
   * frames (initial comment, heartbeats, log events); backfill sets it above the highest replayed
   * id so the boundary between replay and live emits no duplicates.
   */
  record EmitterState(@NonNull SseEmitter emitter, @NonNull AtomicLong nextId) {
  }
}
