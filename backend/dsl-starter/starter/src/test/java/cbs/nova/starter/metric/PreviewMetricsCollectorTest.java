package cbs.nova.starter.metric;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PreviewMetricsCollectorTest {

  @AfterEach
  void tearDown() {
    PreviewMetricsCollector.remove();
    PreviewMetricsCollector.resetLatestSnapshot();
  }

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
  void threadLocalIsolation() throws Exception {
    var mainSnapshot = new PreviewMetricsSnapshot[1];
    var threadSnapshot = new PreviewMetricsSnapshot[1];

    var mainCollector = PreviewMetricsCollector.start();
    mainCollector.recordCall(CallKind.PROCESS);

    var thread = new Thread(() -> {
      var tCollector = PreviewMetricsCollector.start();
      tCollector.recordCall(CallKind.HELPER);
      tCollector.recordExternalCall("http");
      sleep(5);
      threadSnapshot[0] = tCollector.stop();
    });
    thread.start();
    thread.join();

    mainCollector.recordCall(CallKind.TRANSACTION);
    mainSnapshot[0] = mainCollector.stop();

    assertThat(mainSnapshot[0].callCounts()).containsOnlyKeys(CallKind.PROCESS,
            CallKind.TRANSACTION);
    assertThat(mainSnapshot[0].callCounts()).hasSize(2);
    assertThat(threadSnapshot[0].callCounts()).containsOnlyKeys(CallKind.HELPER);
    assertThat(threadSnapshot[0].externalCallCounts()).containsOnlyKeys("http");
    assertThat(threadSnapshot[0].executionDurationMs()).isGreaterThanOrEqualTo(5);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
