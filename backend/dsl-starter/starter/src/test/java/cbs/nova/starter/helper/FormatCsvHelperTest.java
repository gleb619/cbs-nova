package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.CsvOptions;
import cbs.nova.starter.helper.model.FormatCsvIn;
import cbs.nova.starter.helper.model.FormatCsvOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatCsvHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatCsvHelper helper = new FormatCsvHelper();

  @Test
  void emptyRowsFails() {
    Result<FormatCsvOut> result = execute(List.of(), null, null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatCsv.rows is required");
  }

  @Test
  void nullRowsFails() {
    Result<FormatCsvOut> result = execute(null, null, null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("formatCsv.rows is required");
  }

  @Test
  void raggedRowsFails() {
    Result<FormatCsvOut> result = execute(
            List.of(List.of("a", "b"), List.of("c")), null, null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("formatCsv: ragged rows");
  }

  @Test
  void headerRowWidthMismatchFails() {
    Result<FormatCsvOut> result = execute(
            List.of(List.of("a", "b")), List.of("h1"), null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("formatCsv: ragged rows");
  }

  @Test
  void headerRowPrepended() {
    Result<FormatCsvOut> result = execute(
            List.of(List.of("a", "b")), List.of("h1", "h2"), null);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().csv()).isEqualTo("h1,h2\r\na,b\r\n");
  }

  @Test
  void quotesFieldContainingDelimiter() {
    Result<FormatCsvOut> result = execute(
            List.of(List.of("x,y", "2")), null, null);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().csv()).isEqualTo("\"x,y\",2\r\n");
  }

  @Test
  void customDelimiterAndLineSeparator() {
    Result<FormatCsvOut> result = execute(
            List.of(List.of("a", "b"), List.of("c", "d")),
            null,
            new CsvOptions("\t", false, "\n"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().csv()).isEqualTo("a\tb\nc\td\n");
  }

  private Result<FormatCsvOut> execute(
          List<List<String>> rows,
          List<String> headerRow,
          CsvOptions options) {
    var ctx = contextFactory.of(new FormatCsvIn(rows, headerRow, options), ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
