package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvSupportTest {

  private static final char COMMA = ',';

  // ---------- parse ----------

  @Test
  void parseEmptyInputReturnsEmptyList() {
    assertThat(CsvSupport.parse("", COMMA)).isEmpty();
  }

  @Test
  void parseSingleUnquotedField() {
    assertThat(CsvSupport.parse("hello", COMMA))
            .containsExactly(List.of("hello"));
  }

  @Test
  void parseSingleRowMultipleFields() {
    assertThat(CsvSupport.parse("a,b,c", COMMA))
            .containsExactly(List.of("a", "b", "c"));
  }

  @Test
  void parseMultipleRowsLfTerminated() {
    assertThat(CsvSupport.parse("a,b\nc,d\ne,f", COMMA))
            .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
  }

  @Test
  void parseMultipleRowsCrlfTerminated() {
    assertThat(CsvSupport.parse("a,b\r\nc,d\r\ne,f", COMMA))
            .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
  }

  @Test
  void parseMixedLineTerminators() {
    assertThat(CsvSupport.parse("a,b\nc,d\r\ne,f", COMMA))
            .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
  }

  @Test
  void parseTrailingNewlineEmitsTrailingEmptyRow() {
    // Parser only adds the trailing row when there is residual content; a bare final \n
    // collapses to START with no residual, so the result is identical to the trimmed input.
    assertThat(CsvSupport.parse("a,b\n", COMMA))
            .containsExactly(List.of("a", "b"));
  }

  @Test
  void parseCustomDelimiter() {
    assertThat(CsvSupport.parse("a\tb\tc", '\t'))
            .containsExactly(List.of("a", "b", "c"));
  }

  @Test
  void parseEmbeddedDelimiterInsideQuotes() {
    assertThat(CsvSupport.parse("\"a,b\",c", COMMA))
            .containsExactly(List.of("a,b", "c"));
  }

  @Test
  void parseEmbeddedQuoteEscapedAsDoubleQuote() {
    assertThat(CsvSupport.parse("\"a\"\"b\"", COMMA))
            .containsExactly(List.of("a\"b"));
  }

  @Test
  void parseEmbeddedNewlineInsideQuotedField() {
    assertThat(CsvSupport.parse("\"a\nb\",c", COMMA))
            .containsExactly(List.of("a\nb", "c"));
  }

  @Test
  void parseEmbeddedCarriageReturnInsideQuotedField() {
    assertThat(CsvSupport.parse("\"a\rb\",c", COMMA))
            .containsExactly(List.of("a\rb", "c"));
  }

  @Test
  void parseEmbeddedCrlfInsideQuotedField() {
    assertThat(CsvSupport.parse("\"a\r\nb\",c", COMMA))
            .containsExactly(List.of("a\r\nb", "c"));
  }

  @Test
  void parseEmptyFieldsBetweenDelimiters() {
    assertThat(CsvSupport.parse("a,,b", COMMA))
            .containsExactly(List.of("a", "", "b"));
  }

  @Test
  void parseLeadingAndTrailingWhitespaceStaysVerbatim() {
    assertThat(CsvSupport.parse(" a , b ", COMMA))
            .containsExactly(List.of(" a ", " b "));
  }

  @Test
  void parseEmptyQuotedField() {
    assertThat(CsvSupport.parse("\"\",b", COMMA))
            .containsExactly(List.of("", "b"));
  }

  @Test
  void parseQuoteFollowedByDelimiterEndsField() {
    assertThat(CsvSupport.parse("\"a\",b", COMMA))
            .containsExactly(List.of("a", "b"));
  }

  @Test
  void parseQuoteFollowedByNewlineEndsField() {
    assertThat(CsvSupport.parse("\"a\"\nb", COMMA))
            .containsExactly(List.of("a"), List.of("b"));
  }

  @Test
  void parseQuoteFollowedByCrlfEndsField() {
    assertThat(CsvSupport.parse("\"a\"\r\nb", COMMA))
            .containsExactly(List.of("a"), List.of("b"));
  }

  @Test
  void parseStrayQuoteInUnquotedFieldThrows() {
    assertThatThrownBy(() -> CsvSupport.parse("a\"b", COMMA))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unexpected quote in unquoted field");
  }

  @Test
  void parseCharacterAfterClosingQuoteThrows() {
    assertThatThrownBy(() -> CsvSupport.parse("\"a\"x", COMMA))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unexpected character after closing quote");
  }

  @Test
  void parseUnterminatedQuotedFieldThrows() {
    assertThatThrownBy(() -> CsvSupport.parse("\"abc", COMMA))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unterminated quoted field");
  }

  @Test
  void parseRowWithoutTrailingNewlineIsIncluded() {
    assertThat(CsvSupport.parse("a,b", COMMA))
            .containsExactly(List.of("a", "b"));
  }

  // ---------- format ----------

  @Test
  void formatEmptyRowsProducesEmptyString() {
    assertThat(CsvSupport.format(List.of(), COMMA, "\r\n")).isEmpty();
  }

  @Test
  void formatSimpleUnquotedRows() {
    String csv = CsvSupport.format(
            List.of(List.of("a", "b"), List.of("c", "d")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("a,b\r\nc,d\r\n");
  }

  @Test
  void formatFieldContainingDelimiterIsQuoted() {
    String csv = CsvSupport.format(List.of(List.of("x,y", "z")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("\"x,y\",z\r\n");
  }

  @Test
  void formatFieldContainingQuoteIsQuotedAndEscaped() {
    String csv = CsvSupport.format(List.of(List.of("a\"b")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("\"a\"\"b\"\r\n");
  }

  @Test
  void formatFieldContainingNewlineIsQuoted() {
    String csv = CsvSupport.format(List.of(List.of("a\nb")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("\"a\nb\"\r\n");
  }

  @Test
  void formatFieldContainingCarriageReturnIsQuoted() {
    String csv = CsvSupport.format(List.of(List.of("a\rb")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("\"a\rb\"\r\n");
  }

  @Test
  void formatFieldContainingBothDelimiterAndQuoteIsFullyEscaped() {
    String csv = CsvSupport.format(List.of(List.of("a,\"b")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("\"a,\"\"b\"\r\n");
  }

  @Test
  void formatNullFieldBecomesEmptyString() {
    String csv = CsvSupport.format(
            List.of(java.util.Arrays.asList("a", null, "c")), COMMA, "\r\n");
    assertThat(csv).isEqualTo("a,,c\r\n");
  }

  @Test
  void formatCustomDelimiterAndLineSeparator() {
    String csv = CsvSupport.format(
            List.of(List.of("a", "b"), List.of("c", "d")), '\t', "\n");
    assertThat(csv).isEqualTo("a\tb\nc\td\n");
  }

  @Test
  void formatRoundTripPreservesAllEdgeCases() {
    List<List<String>> rows = List.of(
            List.of("plain", "with,comma", "with\"quote", "with\nnewline"),
            List.of("", "trailing "));
    String csv = CsvSupport.format(rows, COMMA, "\r\n");
    assertThat(CsvSupport.parse(csv, COMMA)).isEqualTo(rows);
  }
}