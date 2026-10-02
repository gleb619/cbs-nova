package cbs.nova.starter.sse;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.logging.DryRunLogEvent;
import cbs.nova.starter.logging.DryRunLogEventPublisher;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE fan-out for live dry-run (preview/explain) log lines, keyed by trace/run id. Mirrors
 * {@link ExecutionSseService}: one emitter per id, heartbeat pings, removal on send failure,
 * {@code cbs.sse.max-subscribers} cap. Publishing is a no-op when nobody is subscribed — the
 * {@code DryRunLogBuffer} remains the source of truth for history and replay.
 */
@Slf4j
@Service
public final class DryRunLogSseService implements DryRunLogEventPublisher {

  /**
   * See {@link ExecutionSseService#subscribeLock}. Cross-key atomicity requires a shared monitor:
   * {@code ConcurrentHashMap.compute} alone only serializes per-key.
   */
  private final Object subscribeLock = new Object();
  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
  private final AtomicInteger activeCount = new AtomicInteger();
  private final int maxSubscribers;
  private final ScheduledExecutorService heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "dry-run-log-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                  });

  public DryRunLogSseService(CbsSseProperties properties) {
    this.maxSubscribers = properties.maxSubscribers();
    heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
            StarterConstants.SSE_HEARTBEAT_SECONDS,
            StarterConstants.SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void publish(@NonNull String runId, @NonNull DryRunLogEvent event) {
    SseEmitter emitter = emitters.get(runId);
    if (emitter == null) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().name("log").data(event));
    } catch (IOException ex) {
      log.debug("Failed to send dry-run log SSE event for run {}, removing emitter", runId, ex);
      remove(runId, emitter);
    } catch (IllegalStateException ex) {
      log.debug("Emitter for dry-run run {} already complete", runId);
      remove(runId, emitter);
    }
  }

  public @NonNull SseEmitter subscribe(@NonNull String runId) {
    SseEmitter emitter;
    SseEmitter previous;
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
      emitter = new SseEmitter(StarterConstants.SSE_EMITTER_TIMEOUT_MS);
      emitters.put(runId, emitter);
      if (isNewId) {
        activeCount.incrementAndGet();
      }
    }
    if (previous != null) {
      previous.complete();
    }
    // Stale-callback protection: capture `emitter` as a local. Compare-and-delete in remove() so
    // a callback fired after a newer emitter replaced this one will NOT free the newer slot.
    emitter.onCompletion(() -> remove(runId, emitter));
    emitter.onTimeout(() -> remove(runId, emitter));
    emitter.onError((_) -> remove(runId, emitter));
    try {
      emitter.send(SseEmitter.event().comment("connected"));
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
      boolean removed = emitters.remove(runId, emitter);
      if (removed) {
        activeCount.decrementAndGet();
      }
    }
  }

  private void sendHeartbeats() {
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
      try {
        entry.getValue().send(SseEmitter.event().comment("ping"));
      } catch (IOException | IllegalStateException ex) {
        log.debug("Heartbeat failed for dry-run run {}, removing emitter", entry.getKey(), ex);
        remove(entry.getKey(), entry.getValue());
      }
    }
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
}
