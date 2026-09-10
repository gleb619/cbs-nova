package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcDryRunLoggingContextTest {

  private final DryRunLoggingContext context = new MdcDryRunLoggingContext();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void mdcContextTracksRunIdInsideScope() {
    context.runWithRunId("run-a", () -> {
      assertThat(context.currentRunId()).isEqualTo("run-a");
      assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isEqualTo("run-a");
    });

    assertThat(context.currentRunId()).isNull();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();
  }

  @Test
  void mdcContextClearsRunIdOnException() {
    assertThatThrownBy(() -> context.runWithRunId("run-b", () -> {
      throw new RuntimeException("boom");
    })).isInstanceOf(RuntimeException.class);

    assertThat(context.currentRunId()).isNull();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();
  }

  @Test
  void setRunIdNullRemovesMdcKey() {
    MDC.put(DryRunLoggingContext.RUN_ID_HEADER, "preset");

    context.setRunId(null);

    assertThat(context.currentRunId()).isNull();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();
  }

  @Test
  void setRunIdEmptyStringIsPropagatedAsValue() {
    // An empty run id is a (degenerate) value, not a sentinel for "no run id" — clearing is the
    // signal for that. The MDC impl therefore stores the empty string verbatim.
    context.setRunId("");

    assertThat(context.currentRunId()).isEmpty();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isEmpty();

    context.clearRunId();
    assertThat(context.currentRunId()).isNull();
  }

  @Test
  void runIdHeaderMatchesInterfaceConstant() {
    // The MDC key MUST equal the interface constant — DryRunLoggingContextPropagator writes the
    // same key, so the propagator and the context must agree or the appender will see null.
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();

    context.setRunId("run-x");

    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isEqualTo("run-x");
    assertThat(DryRunLoggingContext.RUN_ID_HEADER).isEqualTo("x-cbs-nova-dry-run-run-id");
  }

  @Test
  void sequentialRunIdsAreIsolated() {
    context.runWithRunId("run-a", () -> {
      assertThat(context.currentRunId()).isEqualTo("run-a");
      assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isEqualTo("run-a");
    });
    assertThat(context.currentRunId()).isNull();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();

    context.runWithRunId("run-b", () -> {
      assertThat(context.currentRunId()).isEqualTo("run-b");
      assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isEqualTo("run-b");
    });
    assertThat(context.currentRunId()).isNull();
    assertThat(MDC.get(DryRunLoggingContext.RUN_ID_HEADER)).isNull();
  }
}
