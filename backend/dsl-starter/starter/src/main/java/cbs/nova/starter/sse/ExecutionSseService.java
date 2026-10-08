package cbs.nova.starter.sse;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.persistence.DslEventRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public final class ExecutionSseService implements ExecutionStatusEventPublisher {

  /**
   * Synchronization root for {@link #subscribe}. The cap check + counter increment + map mutation
   * must be atomic across <em>different</em> keys — {@code ConcurrentHashMap.compute} only
   * serializes per-key, so two threads on distinct ids could otherwise both pass at limit-1. Map
   * mutations inside the block use a plain {@link ConcurrentHashMap} (no external read needs to
   * wait — readers tolerate stale views; the only write that affects counter is the new-key branch
   * and the replacement branch, both inside).
   */
  private final Object subscribeLock = new Object();

  private final Map<String, EmitterState> emitters = new ConcurrentHashMap<>();
  private final AtomicInteger activeCount = new AtomicInteger();
  private final int maxSubscribers;
  private final int backfillMax;
  private final ObjectProvider<DslEventRepository> eventRepositoryProvider;
  private final ObjectMapper objectMapper;
  private final ScheduledExecutorService heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "execution-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                  });

  public ExecutionSseService(
          CbsSseProperties properties,
          ObjectProvider<DslEventRepository> eventRepositoryProvider,
          ObjectMapper objectMapper) {
    this.maxSubscribers = properties.maxSubscribers();
    this.backfillMax = properties.lastEventIdBackfillMax();
    this.eventRepositoryProvider = eventRepositoryProvider;
    this.objectMapper = objectMapper;
    heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
            StarterConstants.SSE_HEARTBEAT_SECONDS,
            StarterConstants.SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void publish(@NonNull String id, @NonNull String status) {
    EmitterState state = emitters.get(id);
    if (state == null) {
      return;
    }
    SseEmitter emitter = state.emitter();
    long eventId = state.nextId().getAndIncrement();
    try {
      emitter.send(SseEmitter.event()
              .id(Long.toString(eventId))
              .name("execution")
              .data(new ExecutionStatusChangedEvent(id, toDisplayStatus(status),
                      Instant.now())));
    } catch (IOException ex) {
      log.debug("Failed to send SSE event for run {}, removing emitter", id, ex);
      remove(id, emitter);
    } catch (IllegalStateException ex) {
      log.debug("Emitter for run {} already complete", id);
      remove(id, emitter);
    }
  }

  /**
   * Opens a new SSE stream for {@code id}. When {@code lastEventId} is positive and a
   * {@link DslEventRepository} is available, the response is preceded by a bounded backfill of
   * every {@code dsl_events} row for this run whose id is strictly greater than
   * {@code lastEventId}, oldest first; the live cursor is set above the highest replayed id so no
   * event is sent twice. With no {@code Last-Event-ID} the live cursor starts at 1 and no backfill
   * is emitted.
   */
  public @NonNull SseEmitter subscribe(@NonNull String id, long lastEventId) {
    EmitterState state;
    EmitterState previous;
    boolean isNewId;
    synchronized (subscribeLock) {
      previous = emitters.get(id);
      if (previous != null) {
        isNewId = false;
      } else {
        int current = activeCount.get();
        if (current >= maxSubscribers) {
          throw new DslSubscriberCapExceededException(maxSubscribers, id);
        }
        isNewId = true;
      }
      SseEmitter emitter = new SseEmitter(StarterConstants.SSE_EMITTER_TIMEOUT_MS);
      AtomicLong cursor = new AtomicLong(1L);
      state = new EmitterState(emitter, cursor);
      emitters.put(id, state);
      if (isNewId) {
        activeCount.incrementAndGet();
      }
    }
    SseEmitter emitter = state.emitter();
    if (previous != null) {
      previous.emitter().complete();
    }
    // Stale-callback protection: capture `state` as a local. The remove(id, emitter) overload
    // of ConcurrentHashMap is a compare-and-delete, so a callback fired after a newer emitter
    // replaced this one will NOT free the newer emitter's slot.
    AtomicLong cursor = state.nextId();
    emitter.onCompletion(() -> remove(id, emitter));
    emitter.onTimeout(() -> remove(id, emitter));
    emitter.onError((_) -> remove(id, emitter));
    if (lastEventId > 0) {
      backfillAndAdvance(id, lastEventId, state);
    }
    try {
      long connectedId = cursor.getAndIncrement();
      emitter.send(SseEmitter.event()
              .id(Long.toString(connectedId))
              .comment("connected"));
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
      EmitterState current = emitters.get(id);
      if (current != null && current.emitter() == emitter) {
        emitters.remove(id);
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
        log.debug("Heartbeat failed for run {}, removing emitter", entry.getKey(), ex);
        remove(entry.getKey(), emitter);
      }
    }
  }

  private void backfillAndAdvance(@NonNull String id, long lastEventId,
          @NonNull EmitterState state) {
    DslEventRepository repository = eventRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return;
    }
    List<DslEventEntity> rows;
    try {
      rows = repository.findByAggregateSince("run", id, lastEventId, backfillMax);
    } catch (RuntimeException ex) {
      // Backfill is best-effort: a transient DB blip must not tear down a freshly opened stream.
      log.debug("Backfill query failed for run {}; resuming live only", id, ex);
      return;
    }
    if (rows.isEmpty()) {
      return;
    }
    long maxSeen = lastEventId;
    for (DslEventEntity row : rows) {
      String status = extractStatus(row);
      if (status == null) {
        continue;
      }
      try {
        state.emitter().send(SseEmitter.event()
                .id(Long.toString(row.id()))
                .name("execution")
                .data(new ExecutionStatusChangedEvent(id, status,
                        row.createdAt() != null ? row.createdAt() : Instant.now())));
      } catch (IOException | IllegalStateException ex) {
        log.debug("Backfill send failed for run {} at id {}", id, row.id(), ex);
        remove(id, state.emitter());
        return;
      }
      if (row.id() > maxSeen) {
        maxSeen = row.id();
      }
    }
    // Set the live cursor above the highest replayed id so no event is emitted twice.
    state.nextId().set(maxSeen + 1);
  }

  @Nullable
  private String extractStatus(@NonNull DslEventEntity row) {
    String payload = row.payloadJson();
    if (payload == null || payload.isBlank()) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(payload);
      JsonNode eventType = node.get("eventType");
      if (eventType == null || !eventType.isString()) {
        return null;
      }
      String type = eventType.asString();
      if ("RunStarted".equals(type)) {
        return "Running";
      }
      JsonNode status = node.get("status");
      if (status == null || !status.isString()) {
        return null;
      }
      return toDisplayStatus(status.asString());
    } catch (Exception ex) {
      log.debug("Failed to parse dsl_events payload for id {}", row.id(), ex);
      return null;
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

  /**
   * Per-emitter cursor. The {@code nextId} cursor hands out monotonically increasing ids for live
   * frames (initial comment, heartbeats, status events); backfill sets it above the highest
   * replayed id so the boundary between replay and live emits no duplicates.
   */
  record EmitterState(@NonNull SseEmitter emitter, @NonNull AtomicLong nextId) {
  }
}
