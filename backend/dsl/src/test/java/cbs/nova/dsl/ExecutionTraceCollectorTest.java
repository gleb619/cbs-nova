package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionTraceCollectorTest {

  @Test
  void collectsEntriesWhileStarted() {
    ExecutionTraceCollector.getInstance().start();
    try {
      ExecutionTraceCollector.getInstance().add("step-1");
      ExecutionTraceCollector.getInstance().add("step-2");
      assertThat(ExecutionTraceCollector.getInstance().snapshot()).containsExactly("step-1",
              "step-2");
    } finally {
      ExecutionTraceCollector.getInstance().stop();
    }
  }

  @Test
  void returnsEmptyAfterStop() {
    ExecutionTraceCollector.getInstance().start();
    ExecutionTraceCollector.getInstance().add("x");
    ExecutionTraceCollector.getInstance().stop();
    assertThat(ExecutionTraceCollector.getInstance().snapshot()).isEmpty();
  }

  @Test
  void addsAreNoopWhenNotStarted() {
    ExecutionTraceCollector.getInstance().add("ignored");
    assertThat(ExecutionTraceCollector.getInstance().snapshot()).isEmpty();
  }
}
