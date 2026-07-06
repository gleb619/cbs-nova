package cbs.nova.starter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExternalCallTracker {

  private static final ThreadLocal<List<CallDetail>> THREAD_LOCAL_CALLS = new ThreadLocal<>();
  private static volatile ExternalCallTracker instance;

  private final List<ExternalCallListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> globalCounts = new ConcurrentHashMap<>();

  public ExternalCallTracker() {
    instance = this;
  }

  public static void startTracking(@NonNull List<CallDetail> container) {
    THREAD_LOCAL_CALLS.set(container);
  }

  public static void stopTracking() {
    THREAD_LOCAL_CALLS.remove();
  }

  public static @Nullable List<CallDetail> getActiveTracking() {
    return THREAD_LOCAL_CALLS.get();
  }

  public static void record(@NonNull String type, @NonNull String target, @NonNull String operation, @Nullable Object payload) {
    if (instance != null) {
      instance.recordCall(type, target, operation, payload);
    } else {
      List<CallDetail> local = THREAD_LOCAL_CALLS.get();
      if (local != null) {
        local.add(new CallDetail(type.toLowerCase(), target, operation, System.currentTimeMillis(),
                payload != null ? Map.of("payload", payload) : Map.of()));
      }
    }
  }

  public void registerListener(@NonNull ExternalCallListener listener) {
    this.listeners.add(listener);
  }

  public void recordCall(@NonNull String type, @NonNull String target, @NonNull String operation, @Nullable Object payload) {
    String normType = type.toLowerCase();
    globalCounts.merge(normType, 1, Integer::sum);

    List<CallDetail> local = THREAD_LOCAL_CALLS.get();
    if (local != null) {
      local.add(new CallDetail(normType, target, operation, System.currentTimeMillis(),
              payload != null ? Map.of("payload", payload) : Map.of()));
    }

    for (ExternalCallListener listener : listeners) {
      try {
        listener.onCall(normType, target, operation, payload);
      } catch (Exception ignored) {
      }
    }
  }

  public @NonNull Map<String, Integer> getGlobalCounts() {
    return Map.copyOf(globalCounts);
  }

  public void resetGlobalCounts() {
    globalCounts.clear();
  }

  public record CallDetail(
          @NonNull String type,
          @NonNull String target,
          @NonNull String operation,
          long timestamp,
          @NonNull Map<String, Object> metadata
  ) {}
}
