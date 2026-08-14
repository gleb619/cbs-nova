package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.exception.DslCompensationException;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.exception.DslValidationException;
import org.junit.jupiter.api.Test;

/**
 * Coverage for the concrete {@link DslException} subclasses that define the public error contract
 * surfaced via {@code ErrorResponse} and mapped by {@code DslExceptionHandler}.
 *
 * <p>
 * These tests guard four things:
 *
 * <ol>
 * <li>Each subclass carries the correct {@link DslErrorCode} (confirmed from source).
 * <li>Message and cause propagate through the constructor.
 * <li>The {@code runId} / {@code exceptionId} are populated by the base class.
 * <li>The subclass's runtime type is preserved when caught or inspected — never erased to the base
 * {@code DslException}.
 * </ol>
 */
class DslExceptionHierarchyTest {

  // ---- DslEntityNotFoundException ---------------------------------------------

  @Test
  void entityNotFoundCarriesEntityNotFoundCode() {
    var ex = new DslEntityNotFoundException("run-1", "missing thing");
    assertThat(ex.code()).isEqualTo(DslErrorCode.ENTITY_NOT_FOUND);
  }

  @Test
  void entityNotFoundPropagatesMessage() {
    var ex = new DslEntityNotFoundException("run-1", "missing thing");
    assertThat(ex.getMessage()).isEqualTo("missing thing");
  }

  @Test
  void entityNotFoundHasNoCause() {
    // Constructor does not accept a cause — must be null.
    var ex = new DslEntityNotFoundException("run-1", "missing thing");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void entityNotFoundPreservesRunIdAndExceptionId() {
    var ex = new DslEntityNotFoundException("run-xyz", "missing thing");
    assertThat(ex.runId()).isEqualTo("run-xyz");
    assertThat(ex.exceptionId()).startsWith("run-xyz:ex:").doesNotEndWith("null");
  }

  @Test
  void entityNotFoundRuntimeTypeIsPreserved() {
    // Caught as DslException it must still be the concrete subclass.
    DslException ex = new DslEntityNotFoundException("run-1", "missing thing");
    assertThat(ex).isExactlyInstanceOf(DslEntityNotFoundException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  // ---- DslValidationException -------------------------------------------------

  @Test
  void validationCarriesValidationFailedCode() {
    var ex = new DslValidationException("run-2", "bad input");
    assertThat(ex.code()).isEqualTo(DslErrorCode.VALIDATION_FAILED);
  }

  @Test
  void validationPropagatesMessage() {
    var ex = new DslValidationException("run-2", "bad input");
    assertThat(ex.getMessage()).isEqualTo("bad input");
  }

  @Test
  void validationHasNoCause() {
    // Constructor does not accept a cause — must be null.
    var ex = new DslValidationException("run-2", "bad input");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void validationPreservesRunIdAndExceptionId() {
    var ex = new DslValidationException("run-abc", "bad input");
    assertThat(ex.runId()).isEqualTo("run-abc");
    assertThat(ex.exceptionId()).startsWith("run-abc:ex:").doesNotEndWith("null");
  }

  @Test
  void validationRuntimeTypeIsPreserved() {
    DslException ex = new DslValidationException("run-2", "bad input");
    assertThat(ex).isExactlyInstanceOf(DslValidationException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  // ---- DslCompensationException -----------------------------------------------

  @Test
  void compensationCarriesCompensationFailedCode() {
    var ex = new DslCompensationException("run-3", "rollback failed", null);
    assertThat(ex.code()).isEqualTo(DslErrorCode.COMPENSATION_FAILED);
  }

  @Test
  void compensationPropagatesMessage() {
    var ex = new DslCompensationException("run-3", "rollback failed", null);
    assertThat(ex.getMessage()).isEqualTo("rollback failed");
  }

  @Test
  void compensationCauseCanBeNull() {
    var ex = new DslCompensationException("run-3", "rollback failed", null);
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void compensationCauseIsPreserved() {
    var cause = new IllegalStateException("root cause");
    var ex = new DslCompensationException("run-3", "rollback failed", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void compensationPreservesRunIdAndExceptionId() {
    var ex = new DslCompensationException("run-cmp", "rollback failed", null);
    assertThat(ex.runId()).isEqualTo("run-cmp");
    assertThat(ex.exceptionId()).startsWith("run-cmp:ex:").doesNotEndWith("null");
  }

  @Test
  void compensationRuntimeTypeIsPreserved() {
    DslException ex = new DslCompensationException("run-3", "rollback failed", null);
    assertThat(ex).isExactlyInstanceOf(DslCompensationException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  // ---- DslExecutionException --------------------------------------------------

  @Test
  void executionCarriesExecutionFailedCode() {
    var ex = new DslExecutionException("run-4", "activity failed", null);
    assertThat(ex.code()).isEqualTo(DslErrorCode.EXECUTION_FAILED);
  }

  @Test
  void executionPropagatesMessage() {
    var ex = new DslExecutionException("run-4", "activity failed", null);
    assertThat(ex.getMessage()).isEqualTo("activity failed");
  }

  @Test
  void executionCauseCanBeNull() {
    var ex = new DslExecutionException("run-4", "activity failed", null);
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void executionCauseIsPreserved() {
    var cause = new RuntimeException("underlying");
    var ex = new DslExecutionException("run-4", "activity failed", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void executionPreservesRunIdAndExceptionId() {
    var ex = new DslExecutionException("run-exec", "activity failed", null);
    assertThat(ex.runId()).isEqualTo("run-exec");
    assertThat(ex.exceptionId()).startsWith("run-exec:ex:").doesNotEndWith("null");
  }

  @Test
  void executionRuntimeTypeIsPreserved() {
    DslException ex = new DslExecutionException("run-4", "activity failed", null);
    assertThat(ex).isExactlyInstanceOf(DslExecutionException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  // ---- Cross-cutting behaviour -----------------------------------------------

  @Test
  void eachExceptionIdIsUnique() {
    // exceptionId embeds a UUID — two throws must never share an id.
    var a = new DslValidationException("run-9", "x");
    var b = new DslValidationException("run-9", "x");
    assertThat(a.exceptionId()).isNotEqualTo(b.exceptionId());
  }

  @Test
  void exceptionIdFormatIsStable() {
    // The "<runId>:ex:<uuid>" format is a wire-contract side channel used in logs and Sentry tags.
    var ex = new DslExecutionException("run-fmt", "boom", null);
    assertThat(ex.exceptionId()).matches("^run-fmt:ex:[0-9a-fA-F-]{36}$");
  }

  @Test
  void subclassesAreUnrelatedToEachOther() {
    // Sanity: the four subclasses are siblings, not in a deeper chain. If a refactor ever
    // makes one extend another, the type assertions above will still pass but this test fails.
    var a = new DslEntityNotFoundException("r", "m");
    var b = new DslValidationException("r", "m");
    var c = new DslCompensationException("r", "m", null);
    var d = new DslExecutionException("r", "m", null);

    assertThat(a).isNotInstanceOf(DslValidationException.class);
    assertThat(b).isNotInstanceOf(DslCompensationException.class);
    assertThat(c).isNotInstanceOf(DslExecutionException.class);
    assertThat(d).isNotInstanceOf(DslEntityNotFoundException.class);
  }

  @Test
  void allSubclassesAreUnchecked() {
    // DslException extends RuntimeException; subclasses must remain unchecked so
    // the Saga/Transactional flow can surface them without forcing throws clauses.
    assertThat(RuntimeException.class).isAssignableFrom(DslEntityNotFoundException.class);
    assertThat(RuntimeException.class).isAssignableFrom(DslValidationException.class);
    assertThat(RuntimeException.class).isAssignableFrom(DslCompensationException.class);
    assertThat(RuntimeException.class).isAssignableFrom(DslExecutionException.class);
  }

  @Test
  void assertJTypeChecksForCaughtSubclass() {
    // Integration-style smoke: throw + catch + verify the same subclass type comes back.
    assertThatThrownBy(
            () -> {
              throw new DslEntityNotFoundException("run-throw", "nope");
            })
            .isExactlyInstanceOf(DslEntityNotFoundException.class)
            .isInstanceOf(DslException.class)
            .hasMessage("nope")
            .matches(t -> ((DslException) t).code() == DslErrorCode.ENTITY_NOT_FOUND)
            .matches(t -> ((DslException) t).runId().equals("run-throw"));
  }
}
