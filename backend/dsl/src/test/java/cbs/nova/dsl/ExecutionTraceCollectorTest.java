package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExecutionTraceCollectorTest {

  @Test
  void collectsEntriesWhileStarted() {
    ExecutionTraceCollector.start();
    try {
      ExecutionTraceCollector.add("step-1");
      ExecutionTraceCollector.add("step-2");
      assertThat(ExecutionTraceCollector.snapshot()).containsExactly("step-1", "step-2");
    } finally {
      ExecutionTraceCollector.stop();
    }
  }

  @Test
  void returnsEmptyAfterStop() {
    ExecutionTraceCollector.start();
    ExecutionTraceCollector.add("x");
    ExecutionTraceCollector.stop();
    assertThat(ExecutionTraceCollector.snapshot()).isEmpty();
  }

  @Test
  void addsAreNoopWhenNotStarted() {
    ExecutionTraceCollector.add("ignored");
    assertThat(ExecutionTraceCollector.snapshot()).isEmpty();
  }
}
