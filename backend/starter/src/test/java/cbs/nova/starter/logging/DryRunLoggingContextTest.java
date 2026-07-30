package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.logging.ScopedValueDryRunLoggingContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class DryRunLoggingContextTest {

  @Test
  void threadLocalContextTracksRunId() {
    DryRunLoggingContext context = new ThreadLocalDryRunLoggingContext();

    context.runWithRunId("run-a", () -> assertThat(context.currentRunId()).isEqualTo("run-a"));

    assertThat(context.currentRunId()).isNull();
  }

  @Test
  void threadLocalContextClearsRunIdOnException() {
    DryRunLoggingContext context = new ThreadLocalDryRunLoggingContext();

    assertThatThrownBy(() -> context.runWithRunId("run-b", () -> {
      throw new RuntimeException("boom");
    })).isInstanceOf(RuntimeException.class);

    assertThat(context.currentRunId()).isNull();
  }

  @Test
  void scopedValueContextTracksRunId() {
    DryRunLoggingContext context = new ScopedValueDryRunLoggingContext();
    AtomicReference<String> observed = new AtomicReference<>();

    context.runWithRunId("run-c", () -> observed.set(context.currentRunId()));

    assertThat(observed.get()).isEqualTo("run-c");
    assertThat(context.currentRunId()).isNull();
  }

  @Test
  void scopedValueContextRejectsExplicitSetRunId() {
    DryRunLoggingContext context = new ScopedValueDryRunLoggingContext();

    assertThatThrownBy(() -> context.setRunId("run-d"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("runWithRunId");
  }

  @Test
  void runIdHeaderConstantIsPresent() {
    assertThat(DryRunLoggingContext.RUN_ID_HEADER).isEqualTo("x-cbs-nova-dry-run-run-id");
  }
}
