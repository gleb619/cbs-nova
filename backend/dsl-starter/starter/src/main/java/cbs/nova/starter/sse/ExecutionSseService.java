package cbs.nova.starter.sse;

import cbs.nova.starter.core.StarterConstants;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public final class ExecutionSseService implements ExecutionStatusEventPublisher {

  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
  private final ScheduledExecutorService heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "execution-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                  });

  public ExecutionSseService() {
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
    SseEmitter emitter = new SseEmitter(StarterConstants.SSE_EMITTER_TIMEOUT_MS);
    SseEmitter previous = emitters.put(id, emitter);
    if (previous != null) {
      previous.complete();
    }
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

  private void remove(@NonNull String id, @NonNull SseEmitter emitter) {
    emitters.remove(id, emitter);
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

  private static @NonNull String toDisplayStatus(@NonNull String status) {
    if (status == null || status.isBlank()) {
      return "Running";
    }
    return status.substring(0, 1).toUpperCase(Locale.ROOT)
            + status.substring(1).toLowerCase(Locale.ROOT);
  }
}
