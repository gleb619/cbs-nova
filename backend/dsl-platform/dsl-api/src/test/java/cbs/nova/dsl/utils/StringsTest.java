package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringsTest {

  @Test
  void camelCaseConvertsToKebabCase() {
    assertThat(Strings.toKebabCase("batchProcessing")).isEqualTo("batch-processing");
  }

  @Test
  void pascalCaseConvertsToKebabCase() {
    assertThat(Strings.toKebabCase("BatchProcessing")).isEqualTo("batch-processing");
  }

  @Test
  void emptyStringStaysEmpty() {
    assertThat(Strings.toKebabCase("")).isEmpty();
  }

  @Test
  void allCapsInsertsSeparatorBeforeEveryUppercaseAfterFirst() {
    assertThat(Strings.toKebabCase("BATCH")).isEqualTo("b-a-t-c-h");
  }

  @Test
  void leadingUppercaseGetsNoLeadingSeparator() {
    assertThat(Strings.toKebabCase("Batch")).isEqualTo("batch");
  }

  @Test
  void acronymRunSplitsEveryUppercase() {
    assertThat(Strings.toKebabCase("XMLHttp")).isEqualTo("x-m-l-http");
  }

  @Test
  void uppercaseAfterNonLetterGetsNoSeparator() {
    assertThat(Strings.toKebabCase("batch2Day")).isEqualTo("batch2day");
  }
}
