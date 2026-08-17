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
    traceCollector.start();
    traceCollector.add("step-1");
    traceCollector.add("step-2");
    assertThat(traceCollector.snapshot()).containsExactly("step-1", "step-2");
    traceCollector.stop();
  }

  @Test
  void returnsEmptyAfterStop() {
    traceCollector.start();
    traceCollector.add("x");
    traceCollector.stop();
    assertThat(traceCollector.snapshot()).isEmpty();
  }

  @Test
  void addsAreNoopWhenNotStarted() {
    traceCollector.add("ignored");
    assertThat(traceCollector.snapshot()).isEmpty();
  }

  @Test
  void addsAreNoopAfterStop() {
    traceCollector.start();
    traceCollector.add("before");
    traceCollector.stop();
    traceCollector.add("after");
    assertThat(traceCollector.snapshot()).isEmpty();
  }

  @Test
  void stopWithoutStartIsSafe() {
    traceCollector.stop();
    assertThat(traceCollector.snapshot()).isEmpty();
  }

  @Test
  void snapshotImmutability() {
    traceCollector.start();
    traceCollector.add("entry");

    List<String> snapshot = traceCollector.snapshot();
    assertThatThrownBy(() -> snapshot.add("mutant"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(traceCollector.snapshot()).containsExactly("entry");

    traceCollector.stop();
  }

  @Test
  void activityThreadSimulation() throws InterruptedException {
    traceCollector.start();

    CountDownLatch added = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);

    Thread activityThread = new Thread(() -> {
      traceCollector.add("activity-entry");
      added.countDown();
      try {
        proceed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    activityThread.start();
    added.await();

    assertThat(traceCollector.snapshot()).containsExactly("activity-entry");

    proceed.countDown();
    activityThread.join();

    assertThat(traceCollector.snapshot()).containsExactly("activity-entry");
    traceCollector.stop();
  }

  @Test
  void perRunIsolation() {
    ExecutionTraceCollector runA = new ExecutionTraceCollector();
    runA.start();
    runA.add("a-1");

    ExecutionTraceCollector runB = new ExecutionTraceCollector();
    runB.start();
    runB.add("b-1");
    runB.add("b-2");

    assertThat(runA.snapshot()).containsExactly("a-1");
    assertThat(runB.snapshot()).containsExactly("b-1", "b-2");

    runA.stop();
    runB.stop();
  }

  @Test
  void exceptionSafetyLeavesNoResidualState() {
    ExecutionTraceCollector collector = new ExecutionTraceCollector();
    collector.start();
    collector.add("entry");

    try {
      throw new RuntimeException("boom");
    } catch (RuntimeException expected) {
      collector.stop();
    }

    assertThat(collector.snapshot()).isEmpty();
  }
}
