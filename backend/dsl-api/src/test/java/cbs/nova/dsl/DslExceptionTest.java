package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DslExceptionTest {

  @Test
  void exceptionIdContainsRunId() {
    var ex = new DslExecutionException("run-abc", "boom", null);
    assertThat(ex.runId()).isEqualTo("run-abc");
    assertThat(ex.exceptionId()).startsWith("run-abc:ex:");
    assertThat(ex.code()).isEqualTo(DslErrorCode.EXECUTION_FAILED);
  }

  @Test
  void hierarchyCodesAreStable() {
    assertThat(new DslEntityNotFoundException("run-x", "not found").code())
            .isEqualTo(DslErrorCode.ENTITY_NOT_FOUND);
    assertThat(new DslValidationException("run-x", "bad").code())
            .isEqualTo(DslErrorCode.VALIDATION_FAILED);
    assertThat(new DslCompensationException("run-x", "comp failed", null).code())
            .isEqualTo(DslErrorCode.COMPENSATION_FAILED);
  }

  @Test
  void causeIsPreserved() {
    var cause = new RuntimeException("root");
    var ex = new DslExecutionException("run-1", "wrap", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
