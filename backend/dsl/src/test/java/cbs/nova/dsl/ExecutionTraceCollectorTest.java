package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionTraceCollectorTest {

  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  @Test
  void collectsEntriesWhileStarted() {
    traceCollector.start();
    try {
      traceCollector.add("step-1");
      traceCollector.add("step-2");
      assertThat(traceCollector.snapshot()).containsExactly("step-1",
              "step-2");
    } finally {
      traceCollector.stop();
    }
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
}
