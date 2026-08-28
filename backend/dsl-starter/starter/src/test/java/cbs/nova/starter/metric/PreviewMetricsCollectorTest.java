package cbs.nova.starter.metric;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PreviewMetricsCollectorTest {

  @Test
  void startStopCapturesExecutionDuration() {
    var collector = PreviewMetricsCollector.start();
    sleep(10);
    var snapshot = collector.stop();
    assertThat(snapshot.executionDurationMs()).isGreaterThanOrEqualTo(10);
  }

  @Test
  void memoryDeltaIsCalculated() {
    var collector = PreviewMetricsCollector.start();
    var snapshot = collector.stop();
    assertThat(snapshot.memoryUsedBytes()).isNotNegative();
  }

  @Test
  void recordCallIncrementsCallCounts() {
    var collector = PreviewMetricsCollector.start();
    collector.recordCall(CallKind.PROCESS);
    collector.recordCall(CallKind.PROCESS);
    collector.recordCall(CallKind.TRANSACTION);
    collector.recordCall(CallKind.HELPER);
    var snapshot = collector.stop();
    assertThat(snapshot.callCounts()).containsAllEntriesOf(Map.of(
            CallKind.PROCESS, 2,
            CallKind.TRANSACTION, 1,
            CallKind.HELPER, 1));
    assertThat(snapshot.callCounts()).doesNotContainKey(CallKind.FUNCTION);
  }

  @Test
  void recordExternalCallIncrementsExternalCallCounts() {
    var collector = PreviewMetricsCollector.start();
    collector.recordExternalCall("database");
    collector.recordExternalCall("database");
    collector.recordExternalCall("http");
    var snapshot = collector.stop();
    assertThat(snapshot.externalCallCounts()).containsAllEntriesOf(Map.of(
            "database", 2,
            "http", 1));
  }

  @Test
  void stopReturnsSameSnapshotOnRepeatedCalls() {
    var collector = PreviewMetricsCollector.start();
    collector.recordCall(CallKind.PROCESS);
    PreviewMetricsSnapshot first = collector.stop();
    assertThat(first.callCounts()).containsEntry(CallKind.PROCESS, 1);

    PreviewMetricsSnapshot second = collector.stop();
    assertThat(second.callCounts()).containsEntry(CallKind.PROCESS, 1);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
