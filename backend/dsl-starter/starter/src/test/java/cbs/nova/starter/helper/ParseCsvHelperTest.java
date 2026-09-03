package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.CsvOptions;
import cbs.nova.starter.helper.model.FormatCsvIn;
import cbs.nova.starter.helper.model.FormatCsvOut;
import cbs.nova.starter.helper.model.ParseCsvIn;
import cbs.nova.starter.helper.model.ParseCsvOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParseCsvHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ParseCsvHelper helper = new ParseCsvHelper();
  private final FormatCsvHelper formatHelper = new FormatCsvHelper();

  @Test
  void roundTrip() {
    String original = "a,b,c\r\n1,2,3\r\n";
    List<List<String>> rows = execute(original).value().rows();
    String formatted = format(rows, null).value().csv();
    assertThat(formatted).isEqualTo(original);
  }

  @Test
  void quotedFieldWithEmbeddedComma() {
    Result<ParseCsvOut> result = execute("\"x,y\",2");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().rows()).containsExactly(List.of("x,y", "2"));
  }

  @Test
  void escapedQuotes() {
    Result<ParseCsvOut> result = execute("\"a\"\"b\",2");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().rows()).containsExactly(List.of("a\"b", "2"));
  }

  @Test
  void quotedFieldWithEmbeddedNewline() {
    Result<ParseCsvOut> result = execute("\"line1\nline2\",b");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().rows()).containsExactly(List.of("line1\nline2", "b"));
  }

  @Test
  void withHeaderDropsFirstRow() {
    Result<ParseCsvOut> result = execute("header1,header2\na,b\nc,d",
            new CsvOptions(null, true, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().rows()).containsExactly(List.of("a", "b"), List.of("c", "d"));
  }

  @Test
  void tabDelimiter() {
    Result<ParseCsvOut> result = execute("a\tb\nc\td", new CsvOptions("\t", false, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().rows())
            .containsExactly(List.of("a", "b"), List.of("c", "d"));
  }

  @Test
  void crLfLfCrAllParseToSameRows() {
    List<List<String>> expected = List.of(List.of("a", "b"), List.of("c", "d"));
    assertThat(execute("a,b\r\nc,d").value().rows()).isEqualTo(expected);
    assertThat(execute("a,b\nc,d").value().rows()).isEqualTo(expected);
    assertThat(execute("a,b\rc,d").value().rows()).isEqualTo(expected);
  }

  @Test
  void nullPayloadFails() {
    Result<ParseCsvOut> result = execute(null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("parseCsv.payload is required");
  }

  @Test
  void blankPayloadFails() {
    Result<ParseCsvOut> result = execute("   ");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("parseCsv.payload is required");
  }

  @Test
  void unterminatedQuoteFails() {
    Result<ParseCsvOut> result = execute("\"abc");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("parseCsv: malformed CSV");
  }

  private Result<ParseCsvOut> execute(String payload) {
    return execute(payload, null);
  }

  private Result<ParseCsvOut> execute(String payload, CsvOptions options) {
    var ctx = contextFactory.of(new ParseCsvIn(payload, options), ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }

  private Result<FormatCsvOut> format(List<List<String>> rows, List<String> headerRow) {
    var ctx = contextFactory.of(new FormatCsvIn(rows, headerRow, null), ExecutionMode.PREVIEW);
    return formatHelper.execute(ctx);
  }
}
