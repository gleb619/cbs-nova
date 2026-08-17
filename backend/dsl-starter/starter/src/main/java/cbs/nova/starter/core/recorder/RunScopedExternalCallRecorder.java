package cbs.nova.starter.core.recorder;

import cbs.nova.starter.core.event.DslExternalCallEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.listener.DslExecutionListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RunScopedExternalCallRecorder implements ExternalCallRecorder {

  private final ThreadLocal<String> currentRunId = new ThreadLocal<>();
  private final ThreadLocal<List<ExternalCall>> threadLocalCalls = new ThreadLocal<>();
  private final List<DslExecutionListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> globalCounts = new ConcurrentHashMap<>();
  private final DslExecutionEventBus eventBus;

  public RunScopedExternalCallRecorder(@Nullable DslExecutionEventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  public void startRun(@NonNull String runId) {
    currentRunId.set(runId);
    threadLocalCalls.set(new ArrayList<>());
  }

  @Override
  public @NonNull List<ExternalCall> finishRun(@NonNull String runId) {
    List<ExternalCall> calls = threadLocalCalls.get();
    currentRunId.remove();
    threadLocalCalls.remove();
    return calls != null ? List.copyOf(calls) : List.of();
  }

  @Override
  public void record(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload) {
    String normType = normalizeType(type);
    globalCounts.merge(normType, 1, Integer::sum);

    Map<String, Object> metadata = new HashMap<>();
    if (payload != null) {
      metadata.put("payload", payload);
    }

    ExternalCall call = new ExternalCall(normType, target, operation,
            System.currentTimeMillis(), Map.copyOf(metadata));

    List<ExternalCall> calls = threadLocalCalls.get();
    if (calls != null) {
      calls.add(call);
    }

    for (DslExecutionListener listener : listeners) {
      try {
        listener.onEvent(new DslExternalCallEvent(
                currentRunId(), normType, target, operation, payload));
      } catch (Exception ignored) {
      }
    }

    if (eventBus != null) {
      eventBus.publish(new DslExternalCallEvent(
              currentRunId(), normType, target, operation, payload));
    }
  }

  @Override
  public void registerListener(@NonNull DslExecutionListener listener) {
    listeners.add(listener);
  }

  @Override
  public @NonNull Map<String, Integer> getGlobalCounts() {
    return Map.copyOf(globalCounts);
  }

  @Override
  public void resetGlobalCounts() {
    globalCounts.clear();
  }

  private @Nullable String currentRunId() {
    return currentRunId.get();
  }

  public static @NonNull String normalizeType(@NonNull String type) {
    String lowerType = type.toLowerCase().trim();
    if (containsAny(lowerType, "database", "jdbc", "db", "sql", "hibernate", "jpa",
            "datasource")) {
      return ExternalCallRecorder.TYPE_DATABASE;
    }
    if (containsAny(lowerType, "http", "rest", "webclient", "resttemplate", "feign",
            "url")) {
      return ExternalCallRecorder.TYPE_HTTP;
    }
    if (containsAny(lowerType, "mq", "jms", "kafka", "amqp", "rabbit", "activemq",
            "messaging")) {
      return ExternalCallRecorder.TYPE_MQ;
    }
    if (containsAny(lowerType, "file", "filesystem", "nio", "fileinput", "fileoutput")) {
      return ExternalCallRecorder.TYPE_FILE_SYSTEM;
    }
    if (containsAny(lowerType, "microservice", "grpc", "thrift", "soap", "rpc")) {
      return ExternalCallRecorder.TYPE_MICROSERVICE;
    }
    if (containsAny(lowerType, "activity", "temporal")) {
      return ExternalCallRecorder.TYPE_ACTIVITY;
    }
    if (containsAny(lowerType, "api", "external", "thirdparty", "service")) {
      return ExternalCallRecorder.TYPE_EXTERNAL_API;
    }
    return ExternalCallRecorder.TYPE_OTHER;
  }

  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }
}
