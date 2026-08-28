package cbs.nova.starter.metric;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewMetricsCollector {

  private final long startTime;
  private final long startMemory;
  private final Map<CallKind, Integer> callCounts = new ConcurrentHashMap<>();
  private final Map<String, Integer> externalCallCounts = new ConcurrentHashMap<>();

  public static PreviewMetricsCollector start() {
    return new PreviewMetricsCollector(System.currentTimeMillis(), usedMemory());
  }

  private PreviewMetricsCollector(long startTime, long startMemory) {
    this.startTime = startTime;
    this.startMemory = startMemory;
  }

  public void recordCall(CallKind kind) {
    callCounts.merge(kind, 1, Integer::sum);
  }

  public void recordExternalCall(String type) {
    externalCallCounts.merge(type, 1, Integer::sum);
  }

  public PreviewMetricsSnapshot stop() {
    long duration = System.currentTimeMillis() - startTime;
    long memoryDelta = usedMemory() - startMemory;
    return new PreviewMetricsSnapshot(
            duration,
            memoryDelta,
            Map.copyOf(callCounts),
            Map.copyOf(externalCallCounts));
  }

  private static long usedMemory() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }
}
