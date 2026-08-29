package cbs.nova.starter.core.recorder;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.listener.DslExecutionListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Run-scoped external-call recorder keyed by runId instead of thread identity.
 *
 * <p>
 * Calls are accumulated in a bounded per-run map so that worker threads used by Temporal can record
 * calls for the same run without relying on ThreadLocal state. Abandoned runs are evicted by an
 * insertion-order capacity bound so an orphaned entry cannot leak memory forever.
 */
public final class RunIdKeyedExternalCallRecorder implements ExternalCallRecorder {

  /**
   * Maximum number of distinct runs retained. A manual capacity bound is sufficient at this scale
   * while avoiding an extra caching dependency.
   */
  private static final int CAPACITY = 100;

  /**
   * Maximum number of external calls retained per run; when exceeded the oldest entries are dropped
   * so a single run cannot grow its history without bound.
   */
  private static final int MAX_CALLS_PER_RUN = 100;

  private final Map<String, List<ExternalCall>> callsByRunId = new ConcurrentHashMap<>();
  private final Deque<String> runOrder = new ConcurrentLinkedDeque<>();
  // Tracks runs explicitly because ConcurrentLinkedDeque#size is an O(n) best-effort scan that may
  // undercount under concurrent writes, which would silently skip evictions.
  private final AtomicInteger trackedRuns = new AtomicInteger();

  private final List<DslExecutionListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> globalCounts = new ConcurrentHashMap<>();
  private final DryRunLoggingContext dryRunLoggingContext;
  private final DslExecutionEventBus eventBus;

  public RunIdKeyedExternalCallRecorder(@NonNull DryRunLoggingContext dryRunLoggingContext,
          @Nullable DslExecutionEventBus eventBus) {
    this.dryRunLoggingContext = dryRunLoggingContext;
    this.eventBus = eventBus;
  }

  @Override
  public void startRun(@NonNull String runId) {
    dryRunLoggingContext.setRunId(runId);
    AtomicBoolean firstForRun = new AtomicBoolean();
    callsByRunId.compute(runId, (id, current) -> {
      if (current != null) {
        return current;
      }
      runOrder.addLast(runId);
      trackedRuns.incrementAndGet();
      firstForRun.set(true);
      return new ArrayList<>();
    });
    if (firstForRun.get()) {
      evictOverflowingRuns();
    }
  }

  @Override
  public @NonNull List<ExternalCall> finishRun(@NonNull String runId) {
    List<ExternalCall> calls = callsByRunId.remove(runId);
    if (calls != null) {
      runOrder.remove(runId);
      trackedRuns.decrementAndGet();
    }
    String currentRunId = dryRunLoggingContext.currentRunId();
    if (runId.equals(currentRunId)) {
      dryRunLoggingContext.clearRunId();
    }
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

    String runId = dryRunLoggingContext.currentRunId();
    if (runId != null) {
      AtomicBoolean firstForRun = new AtomicBoolean();
      callsByRunId.compute(runId, (id, current) -> {
        List<ExternalCall> updated = new ArrayList<>(
                current == null ? 1 : current.size() + 1);
        if (current != null) {
          updated.addAll(current);
        } else {
          runOrder.addLast(runId);
          trackedRuns.incrementAndGet();
          firstForRun.set(true);
        }
        updated.add(call);
        if (updated.size() > MAX_CALLS_PER_RUN) {
          updated.remove(0);
        }
        return updated;
      });
      if (firstForRun.get()) {
        evictOverflowingRuns();
      }
    }

    for (DslExecutionListener listener : listeners) {
      try {
        listener.onEvent(new DslExternalCallEvent(runId, normType, target, operation, payload));
      } catch (Exception ignored) {
      }
    }

    if (eventBus != null) {
      eventBus.publish(new DslExternalCallEvent(runId, normType, target, operation, payload));
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

  private void evictOverflowingRuns() {
    while (trackedRuns.get() > CAPACITY) {
      String oldest = runOrder.pollFirst();
      if (oldest == null) {
        return;
      }
      // Removing the run entry also discards its calls. A null result means a concurrent
      // finishRun already dropped it (and already decremented the counter), so keep draining stale
      // ids instead of double-counting.
      if (callsByRunId.remove(oldest) != null) {
        trackedRuns.decrementAndGet();
      }
    }
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
