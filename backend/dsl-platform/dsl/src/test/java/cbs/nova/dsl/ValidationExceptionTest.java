package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;

class ValidationExceptionTest {

  @Test
  void oldStringConstructorStillWorksAndExposesMessages() {
    var ex = new ValidationException(List.of("first", "second"));

    assertThat(ex.errors()).containsExactly("first", "second");
    assertThat(ex.issues()).hasSize(2);
    assertThat(ex.issues().get(0).code()).isNull();
    assertThat(ex.issues().get(0).message()).isEqualTo("first");
    assertThat(ex.getMessage()).contains("first", "second");
  }

  @Test
  void structuredFactoryPreservesCodesAndMessages() {
    var ex = ValidationException.of(List.of(
            new ValidationIssue(DiagnosticCodes.BLANK_PROCESS_NAME, "Process has blank name"),
            new ValidationIssue(DiagnosticCodes.DUPLICATE_NAME, "Duplicate name: X")));

    assertThat(ex.errors()).containsExactly("Process has blank name", "Duplicate name: X");
    assertThat(ex.issues()).extracting(ValidationIssue::code)
            .containsExactly(DiagnosticCodes.BLANK_PROCESS_NAME, DiagnosticCodes.DUPLICATE_NAME);
    assertThat(ex.issues()).extracting(ValidationIssue::message)
            .containsExactly("Process has blank name", "Duplicate name: X");
  }

  @Test
  void emptyIssuesAreAllowed() {
    var ex = ValidationException.of(List.of());

    assertThat(ex.errors()).isEmpty();
    assertThat(ex.issues()).isEmpty();
  }
}
