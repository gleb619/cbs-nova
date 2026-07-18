package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the {@link DslErrorCode} enum.
 *
 * <p>
 * The names of these constants are part of the public wire contract: {@code
 * DslExceptionHandler} serialises {@code ex.code().name()} verbatim into the {@code code} field of
 * {@code ErrorResponse}. Reordering, renaming, or removing a constant would silently break REST
 * consumers. If you need to change a name, treat it as a breaking API change.
 */
class DslErrorCodeTest {

  @Test
  void declaredConstantsAreExactlyTheseSix() {
    // Locked set. If you intentionally add a new code, add an assertion below for
    // its name and ordinal — do not just rely on this test passing.
    assertThat(DslErrorCode.values())
            .containsExactly(
                    DslErrorCode.EXECUTION_FAILED,
                    DslErrorCode.ENTITY_NOT_FOUND,
                    DslErrorCode.VALIDATION_FAILED,
                    DslErrorCode.COMPENSATION_FAILED,
                    DslErrorCode.BAD_REQUEST,
                    DslErrorCode.INTERNAL_ERROR);
  }

  @Test
  void namesAreStable() {
    // These string literals are part of the REST contract. Do not change.
    assertThat(DslErrorCode.EXECUTION_FAILED.name()).isEqualTo("EXECUTION_FAILED");
    assertThat(DslErrorCode.ENTITY_NOT_FOUND.name()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(DslErrorCode.VALIDATION_FAILED.name()).isEqualTo("VALIDATION_FAILED");
    assertThat(DslErrorCode.COMPENSATION_FAILED.name()).isEqualTo("COMPENSATION_FAILED");
    assertThat(DslErrorCode.BAD_REQUEST.name()).isEqualTo("BAD_REQUEST");
    assertThat(DslErrorCode.INTERNAL_ERROR.name()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void ordinalsAreStable() {
    // Ordinals are stable. valueOf() round-trip + ordinal identity keeps the
    // enum defensible against accidental reordering.
    assertThat(DslErrorCode.EXECUTION_FAILED.ordinal()).isEqualTo(0);
    assertThat(DslErrorCode.ENTITY_NOT_FOUND.ordinal()).isEqualTo(1);
    assertThat(DslErrorCode.VALIDATION_FAILED.ordinal()).isEqualTo(2);
    assertThat(DslErrorCode.COMPENSATION_FAILED.ordinal()).isEqualTo(3);
    assertThat(DslErrorCode.BAD_REQUEST.ordinal()).isEqualTo(4);
    assertThat(DslErrorCode.INTERNAL_ERROR.ordinal()).isEqualTo(5);
  }

  @Test
  void valueOfRoundTripsByName() {
    for (DslErrorCode code : DslErrorCode.values()) {
      assertThat(DslErrorCode.valueOf(code.name())).isSameAs(code);
    }
  }
}
