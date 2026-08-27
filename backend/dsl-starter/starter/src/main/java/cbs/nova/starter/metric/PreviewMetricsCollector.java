package cbs.nova.starter.metric;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewMetricsCollector {

  private static final ThreadLocal<PreviewMetricsCollector> INSTANCE = new ThreadLocal<>();
  private static volatile PreviewMetricsSnapshot latestSnapshot = null;

  private long startTime;
  private long startMemory;
  private final Map<CallKind, Integer> callCounts = new ConcurrentHashMap<>();
  private final Map<String, Integer> externalCallCounts = new ConcurrentHashMap<>();

  public static PreviewMetricsCollector start() {
    var collector = new PreviewMetricsCollector();
    collector.startTime = System.currentTimeMillis();
    collector.startMemory = usedMemory();
    INSTANCE.set(collector);
    return collector;
  }

  public PreviewMetricsSnapshot stop() {
    INSTANCE.remove();
    long duration = System.currentTimeMillis() - startTime;
    long memoryDelta = usedMemory() - startMemory;
    var snapshot = new PreviewMetricsSnapshot(
            duration,
            memoryDelta,
            Map.copyOf(callCounts),
            Map.copyOf(externalCallCounts));
    latestSnapshot = snapshot;
    return snapshot;
  }

  public PreviewMetricsSnapshot getSnapshot() {
    long duration = System.currentTimeMillis() - startTime;
    long memoryDelta = usedMemory() - startMemory;
    return new PreviewMetricsSnapshot(
            duration,
            memoryDelta,
            Map.copyOf(callCounts),
            Map.copyOf(externalCallCounts));
  }

  public void recordCall(CallKind kind) {
    callCounts.merge(kind, 1, Integer::sum);
  }

  public void recordExternalCall(String type) {
    externalCallCounts.merge(type, 1, Integer::sum);
  }

  public static @Nullable PreviewMetricsCollector current() {
    return INSTANCE.get();
  }

  public static void remove() {
    INSTANCE.remove();
  }

  public static @Nullable PreviewMetricsSnapshot getLatestSnapshot() {
    return latestSnapshot;
  }

  public static void resetLatestSnapshot() {
    latestSnapshot = null;
  }

  private static long usedMemory() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }
}
