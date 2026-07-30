package cbs.nova.starter;

import cbs.nova.starter.listeners.ExternalCallListener;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
// TODO: refactor to a better system with listeners/interceptors, for better adding of new features
public class ExternalCallTracker {

  public static final String TYPE_DATABASE = "database";
  public static final String TYPE_HTTP = "http";
  public static final String TYPE_MQ = "mq";
  public static final String TYPE_FILE_SYSTEM = "filesystem";
  public static final String TYPE_EXTERNAL_API = "external_api";
  public static final String TYPE_MICROSERVICE = "microservice";
  public static final String TYPE_ACTIVITY = "activity";
  public static final String TYPE_OTHER = "other";
  private final ThreadLocal<List<CallDetail>> threadLocalCalls = new ThreadLocal<>();
  private final List<ExternalCallListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> globalCounts = new ConcurrentHashMap<>();

  public void startTracking(@NonNull List<CallDetail> container) {
    threadLocalCalls.set(container);
  }

  public void stopTracking() {
    threadLocalCalls.remove();
  }

  public @Nullable List<CallDetail> getActiveTracking() {
    return threadLocalCalls.get();
  }

  public void record(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    String normType = normalizeType(type);
    globalCounts.merge(normType, 1, Integer::sum);

    List<CallDetail> local = threadLocalCalls.get();
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

  public void registerListener(@NonNull ExternalCallListener listener) {
    this.listeners.add(listener);
  }

  private String normalizeType(String type) {
    String lowerType = type.toLowerCase().trim();

    if (lowerType.contains("database") || lowerType.contains("jdbc")
            || lowerType.contains("db") || lowerType.contains("sql")
            || lowerType.contains("hibernate") || lowerType.contains("jpa")
            || lowerType.contains("datasource")) {
      return TYPE_DATABASE;
    }

    if (lowerType.contains("http") || lowerType.contains("rest") || lowerType.contains("webclient")
            ||
            lowerType.contains("resttemplate") || lowerType.contains("feign")
            || lowerType.contains("url")) {
      return TYPE_HTTP;
    }

    if (lowerType.contains("mq") || lowerType.contains("jms") || lowerType.contains("kafka") ||
            lowerType.contains("amqp") || lowerType.contains("rabbit")
            || lowerType.contains("activemq")
            || lowerType.contains("messaging")) {
      return TYPE_MQ;
    }

    if (lowerType.contains("file") || lowerType.contains("filesystem") || lowerType.contains("nio")
            ||
            lowerType.contains("fileinput") || lowerType.contains("fileoutput")) {
      return TYPE_FILE_SYSTEM;
    }

    if (lowerType.contains("microservice") || lowerType.contains("grpc")
            || lowerType.contains("thrift") ||
            lowerType.contains("soap") || lowerType.contains("rpc")) {
      return TYPE_MICROSERVICE;
    }

    if (lowerType.contains("activity") || lowerType.contains("temporal")) {
      return TYPE_ACTIVITY;
    }

    if (lowerType.contains("api") || lowerType.contains("external")
            || lowerType.contains("thirdparty") ||
            lowerType.contains("service")) {
      return TYPE_EXTERNAL_API;
    }

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
