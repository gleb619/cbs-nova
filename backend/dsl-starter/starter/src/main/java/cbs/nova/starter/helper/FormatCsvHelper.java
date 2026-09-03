package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.CsvOptions;
import cbs.nova.starter.helper.model.FormatCsvIn;
import cbs.nova.starter.helper.model.FormatCsvOut;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Formats a list of rows into an RFC 4180 CSV string.
 *
 * <p>
 * An optional {@code headerRow} is prepended to the output. All rows (and the header, if present)
 * must have the same width; ragged input is rejected.
 */
@Helper(name = "formatCsv")
public class FormatCsvHelper implements Executable<FormatCsvIn, FormatCsvOut> {

  @Override
  public @NonNull Result<FormatCsvOut> execute(@NonNull Context<FormatCsvIn> ctx) {
    try {
      FormatCsvIn input = ctx.body();
      List<List<String>> rows = input.rows();
      if (rows == null || rows.isEmpty()) {
        return Result.failure(new IllegalArgumentException("formatCsv.rows is required"));
      }
      CsvOptions options = input.options();
      char delimiter = resolveDelimiter(options);
      String lineSeparator = resolveLineSeparator(options);
      List<String> headerRow = input.headerRow();

      int expectedWidth = headerRow != null ? headerRow.size() : rows.get(0).size();
      if (headerRow != null && headerRow.size() != rows.get(0).size()) {
        return Result.failure(new IllegalArgumentException("formatCsv: ragged rows"));
      }
      for (List<String> row : rows) {
        if (row == null || row.size() != expectedWidth) {
          return Result.failure(new IllegalArgumentException("formatCsv: ragged rows"));
        }
      }

      List<List<String>> outputRows = new ArrayList<>(rows.size() + (headerRow != null ? 1 : 0));
      if (headerRow != null) {
        outputRows.add(headerRow);
      }
      outputRows.addAll(rows);

      String csv = CsvSupport.format(outputRows, delimiter, lineSeparator);
      return Result.success(new FormatCsvOut(csv));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static char resolveDelimiter(CsvOptions options) {
    String delimiter = options != null ? options.delimiter() : null;
    if (delimiter == null || delimiter.isEmpty()) {
      return ',';
    }
    if (delimiter.length() != 1) {
      throw new IllegalArgumentException("formatCsv: delimiter must be exactly one character");
    }
    return delimiter.charAt(0);
  }

  private static String resolveLineSeparator(CsvOptions options) {
    String separator = options != null ? options.lineSeparator() : null;
    return (separator == null || separator.isEmpty()) ? "\r\n" : separator;
  }
}
