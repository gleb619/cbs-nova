package cbs.nova.starter.sse;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
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

@Slf4j
@Service
public final class ExecutionSseService implements ExecutionStatusEventPublisher {

  /**
   * Synchronization root for {@link #subscribe(String)}. The cap check + counter increment + map
   * mutation must be atomic across <em>different</em> keys — {@code ConcurrentHashMap.compute} only
   * serializes per-key, so two threads on distinct ids could otherwise both pass at limit-1. Map
   * mutations inside the block use a plain {@link ConcurrentHashMap} (no external read needs to
   * wait — readers tolerate stale views; the only write that affects counter is the new-key branch
   * and the replacement branch, both inside).
   */
  private final Object subscribeLock = new Object();
  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
  private final AtomicInteger activeCount = new AtomicInteger();
  private final int maxSubscribers;
  private final ScheduledExecutorService heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "execution-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                  });

  public ExecutionSseService(CbsSseProperties properties) {
    this.maxSubscribers = properties.maxSubscribers();
    heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
            StarterConstants.SSE_HEARTBEAT_SECONDS,
            StarterConstants.SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void publish(@NonNull String id, @NonNull String status) {
    SseEmitter emitter = emitters.get(id);
    if (emitter == null) {
      return;
    }
    try {
      emitter.send(SseEmitter.event()
              .name("execution")
              .data(new ExecutionStatusChangedEvent(id, toDisplayStatus(status), Instant.now())));
    } catch (IOException ex) {
      log.debug("Failed to send SSE event for run {}, removing emitter", id, ex);
      remove(id, emitter);
    } catch (IllegalStateException ex) {
      log.debug("Emitter for run {} already complete", id);
      remove(id, emitter);
    }
  }

  public @NonNull SseEmitter subscribe(@NonNull String id) {
    SseEmitter emitter;
    SseEmitter previous;
    boolean isNewId;
    synchronized (subscribeLock) {
      previous = emitters.get(id);
      if (previous != null) {
        // Same-id re-subscribe → replacement. Cap is not consumed again.
        isNewId = false;
      } else {
        int current = activeCount.get();
        if (current >= maxSubscribers) {
          throw new DslSubscriberCapExceededException(maxSubscribers, id);
        }
        isNewId = true;
      }
      emitter = new SseEmitter(StarterConstants.SSE_EMITTER_TIMEOUT_MS);
      emitters.put(id, emitter);
      if (isNewId) {
        activeCount.incrementAndGet();
      }
    }
    if (previous != null) {
      previous.complete();
    }
    // Stale-callback protection: capture `emitter` as a local. The remove(id, emitter) overload
    // of ConcurrentHashMap is a compare-and-delete, so a callback fired after a newer emitter
    // replaced this one will NOT free the newer emitter's slot.
    emitter.onCompletion(() -> remove(id, emitter));
    emitter.onTimeout(() -> remove(id, emitter));
    emitter.onError((_) -> remove(id, emitter));
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException | IllegalStateException ex) {
      log.debug("Failed to send initial SSE comment for run {}", id, ex);
    }
    return emitter;
  }

  public int subscriberCount(@NonNull String id) {
    return emitters.containsKey(id) ? 1 : 0;
  }

  public int activeSubscriberCount() {
    return activeCount.get();
  }

  public int maxSubscribers() {
    return maxSubscribers;
  }

  private void remove(@NonNull String id, @NonNull SseEmitter emitter) {
    // Compare-and-delete on the map so we never evict a NEWER emitter that replaced this one.
    // Then free the slot only if we actually removed our entry (avoid double-decrement on
    // overlapping completion + heartbeat paths).
    synchronized (subscribeLock) {
      boolean removed = emitters.remove(id, emitter);
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
        log.debug("Heartbeat failed for run {}, removing emitter", entry.getKey(), ex);
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

  private static @NonNull String toDisplayStatus(@NonNull String status) {
    if (status == null || status.isBlank()) {
      return "Running";
    }
    return status.substring(0, 1).toUpperCase(Locale.ROOT)
            + status.substring(1).toLowerCase(Locale.ROOT);
  }
}
