package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

class ExecutionTraceCollectorTest {

  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  @Test
  void collectsEntriesWhileStarted() {
    String runId = "run-1";
    traceCollector.start(runId);
    try {
      traceCollector.add(runId, "step-1");
      traceCollector.add(runId, "step-2");
      assertThat(traceCollector.snapshot(runId)).containsExactly("step-1", "step-2");
    } finally {
      traceCollector.stop(runId);
    }
  }

  @Test
  void returnsEmptyAfterStop() {
    String runId = "run-2";
    traceCollector.start(runId);
    traceCollector.add(runId, "x");
    traceCollector.stop(runId);
    assertThat(traceCollector.snapshot(runId)).isEmpty();
  }

  @Test
  void addsAreNoopWhenNotStarted() {
    String runId = "run-3";
    traceCollector.add(runId, "ignored");
    assertThat(traceCollector.snapshot(runId)).isEmpty();
  }

  @Test
  void concurrentRunIdIsolation() {
    String runA = "run-A";
    String runB = "run-B";
    traceCollector.start(runA);
    traceCollector.start(runB);
    traceCollector.add(runA, "a-1");
    traceCollector.add(runB, "b-1");
    traceCollector.add(runA, "a-2");
    traceCollector.add(runB, "b-2");

    assertThat(traceCollector.snapshot(runA)).containsExactly("a-1", "a-2");
    assertThat(traceCollector.snapshot(runB)).containsExactly("b-1", "b-2");

    traceCollector.stop(runA);
    traceCollector.stop(runB);
  }

  @Test
  void orphanStopSafety() {
    assertThat(traceCollector.snapshot("never-started")).isEmpty();
    traceCollector.stop("never-started");
    assertThat(traceCollector.snapshot("never-started")).isEmpty();
  }

  @Test
  void snapshotImmutability() {
    String runId = "run-immutable";
    traceCollector.start(runId);
    traceCollector.add(runId, "entry");

    List<String> snapshot = traceCollector.snapshot(runId);
    assertThatThrownBy(() -> snapshot.add("mutant"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(traceCollector.snapshot(runId)).containsExactly("entry");

    traceCollector.stop(runId);
  }

  @Test
  void activityThreadSimulation() throws InterruptedException {
    String runId = "cross-thread-run";
    traceCollector.start(runId);

    CountDownLatch added = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);

    Thread activityThread = new Thread(() -> {
      traceCollector.add(runId, "activity-entry");
      added.countDown();
      try {
        proceed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    activityThread.start();
    added.await();

    assertThat(traceCollector.snapshot(runId)).containsExactly("activity-entry");

    proceed.countDown();
    activityThread.join();

    assertThat(traceCollector.snapshot(runId)).containsExactly("activity-entry");
    traceCollector.stop(runId);
  }
}
