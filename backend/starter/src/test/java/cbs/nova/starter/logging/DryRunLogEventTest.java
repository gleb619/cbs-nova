package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;

class DryRunLogEventTest {

  @Test
  void recordHoldsExpectedFields() {
    var mdc = Map.of("key", "value");
    var event = new DryRunLogEvent("INFO", "hello", 12345L, mdc, "run-1");

    assertThat(event.level()).isEqualTo("INFO");
    assertThat(event.message()).isEqualTo("hello");
    assertThat(event.timestampMillis()).isEqualTo(12345L);
    assertThat(event.mdc()).containsEntry("key", "value");
    assertThat(event.runId()).isEqualTo("run-1");
  }

  @Test
  void mdcIsDefensivelyCopied() {
    var mutable = new java.util.HashMap<String, String>();
    mutable.put("k", "v");
    var event = new DryRunLogEvent("INFO", "hello", 1L, mutable, null);

    mutable.put("k", "changed");

    assertThat(event.mdc()).containsEntry("k", "v");
  }
}
