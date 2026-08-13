package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.junit.jupiter.api.Test;

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
  void runIdHeaderConstantIsPresent() {
    assertThat(DryRunLoggingContext.RUN_ID_HEADER).isEqualTo("x-cbs-nova-dry-run-run-id");
  }
}
