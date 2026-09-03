package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class UtilTest {

  @ParameterizedTest(name = "[{index}] {2}")
  @MethodSource("escapeCases")
  void escapesStrings(String input, String expected, @SuppressWarnings("unused") String label) {
    assertThat(Util.escapeJavaString(input)).isEqualTo(expected);
  }

  static Stream<Arguments> escapeCases() {
    return Stream.of(
            Arguments.of("", "", "empty string"),
            Arguments.of("a", "a", "letter passes through"),
            Arguments.of("7", "7", "digit passes through"),
            Arguments.of("\"", "\\\"", "double quote escaped"),
            Arguments.of("\\", "\\\\", "single backslash doubled"),
            Arguments.of("\b", "\\b", "backspace escape preserved"),
            Arguments.of("\f", "\\f", "form feed escape preserved"),
            Arguments.of("\n", "\\n", "newline escape preserved"),
            Arguments.of("\r", "\\r", "carriage return escape preserved"),
            Arguments.of("\t", "\\t", "tab escape preserved"),
            Arguments.of("\u0000", "\\u" + "0000", "NUL as lowercase four-digit hex"),
            Arguments.of("\u0001", "\\u" + "0001", "SOH as lowercase four-digit hex"),
            Arguments.of("\u001F", "\\u" + "001f", "US as lowercase four-digit hex"),
            Arguments.of("\u20AC", "\u20AC", "euro sign passes through"),
            Arguments.of("\uD83D\uDE00", "\uD83D\uDE00", "emoji passes through"));
  }

  @Test
  void escapesMixedContentInOnePass() {
    String input = "a\"b\\c\n\t\u0000";
    assertThat(Util.escapeJavaString(input))
            .isEqualTo("a\\\"b\\\\c\\n\\t" + "\\u" + "0000");
  }

  @Test
  void treatsAlreadyEscapedInputLiterally() {
    String input = "\\\"";
    assertThat(Util.escapeJavaString(input)).isEqualTo("\\\\\\\"");
  }
}
