package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DslErrorCodeTest {

  @Test
  void declaredConstantsAreExactlyTheseSix() {
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
    assertThat(DslErrorCode.EXECUTION_FAILED.name()).isEqualTo("EXECUTION_FAILED");
    assertThat(DslErrorCode.ENTITY_NOT_FOUND.name()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(DslErrorCode.VALIDATION_FAILED.name()).isEqualTo("VALIDATION_FAILED");
    assertThat(DslErrorCode.COMPENSATION_FAILED.name()).isEqualTo("COMPENSATION_FAILED");
    assertThat(DslErrorCode.BAD_REQUEST.name()).isEqualTo("BAD_REQUEST");
    assertThat(DslErrorCode.INTERNAL_ERROR.name()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void ordinalsAreStable() {
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
