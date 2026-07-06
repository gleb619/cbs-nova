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
  static volatile ExternalCallTracker instance;

  private final List<ExternalCallListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> globalCounts = new ConcurrentHashMap<>();

  // Enhanced categorization for better external call tracking
  public static final String TYPE_DATABASE = "database";
  public static final String TYPE_HTTP = "http";
  public static final String TYPE_MQ = "mq";
  public static final String TYPE_FILE_SYSTEM = "filesystem";
  public static final String TYPE_EXTERNAL_API = "external_api";
  public static final String TYPE_MICROSERVICE = "microservice";
  public static final String TYPE_OTHER = "other";

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

  public static void record(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    if (instance != null) {
      instance.recordCall(type, target, operation, payload);
    } else {
      List<CallDetail> local = THREAD_LOCAL_CALLS.get();
      if (local != null) {
        local.add(new CallDetail(normalizeType(type), target, operation, System.currentTimeMillis(),
                payload != null ? Map.of("payload", payload) : Map.of()));
      }
    }
  }

  public void registerListener(@NonNull ExternalCallListener listener) {
    this.listeners.add(listener);
  }

  public void recordCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    String normType = normalizeType(type);
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

  /**
   * Normalizes external call types to standard categories for better tracking and visualization
   */
  private static String normalizeType(String type) {
    String lowerType = type.toLowerCase().trim();

    // Database-related calls
    if (lowerType.contains("jdbc") || lowerType.contains("db") || lowerType.contains("sql") ||
            lowerType.contains("hibernate") || lowerType.contains("jpa")
            || lowerType.contains("datasource")) {
      return TYPE_DATABASE;
    }

    // HTTP-related calls
    if (lowerType.contains("http") || lowerType.contains("rest") || lowerType.contains("webclient")
            ||
            lowerType.contains("resttemplate") || lowerType.contains("feign")
            || lowerType.contains("url")) {
      return TYPE_HTTP;
    }

    // Message Queue-related calls
    if (lowerType.contains("mq") || lowerType.contains("jms") || lowerType.contains("kafka") ||
            lowerType.contains("amqp") || lowerType.contains("rabbit")
            || lowerType.contains("activemq")) {
      return TYPE_MQ;
    }

    // File system calls
    if (lowerType.contains("file") || lowerType.contains("filesystem") || lowerType.contains("nio")
            ||
            lowerType.contains("fileinput") || lowerType.contains("fileoutput")) {
      return TYPE_FILE_SYSTEM;
    }

    // Microservice calls
    if (lowerType.contains("microservice") || lowerType.contains("grpc")
            || lowerType.contains("thrift") ||
            lowerType.contains("soap") || lowerType.contains("rpc")) {
      return TYPE_MICROSERVICE;
    }

    // External API calls
    if (lowerType.contains("api") || lowerType.contains("external")
            || lowerType.contains("thirdparty") ||
            lowerType.contains("service")) {
      return TYPE_EXTERNAL_API;
    }

    // Default to other
    return TYPE_OTHER;
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
          @NonNull Map<String, Object> metadata) {
  }
}
