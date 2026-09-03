package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.CsvOptions;
import cbs.nova.starter.helper.model.ParseCsvIn;
import cbs.nova.starter.helper.model.ParseCsvOut;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Parses an RFC 4180 CSV payload into a list of rows.
 *
 * <p>
 * Options default to delimiter {@code ","}, {@code withHeader = false}, and line separator
 * {@code "\r\n"}. Setting {@code withHeader = true} drops the first parsed row.
 */
@Helper(name = "parseCsv")
public class ParseCsvHelper implements Executable<ParseCsvIn, ParseCsvOut> {

  @Override
  public @NonNull Result<ParseCsvOut> execute(@NonNull Context<ParseCsvIn> ctx) {
    try {
      ParseCsvIn input = ctx.body();
      if (input.payload() == null || input.payload().isBlank()) {
        return Result.failure(new IllegalArgumentException("parseCsv.payload is required"));
      }
      CsvOptions options = input.options();
      char delimiter = resolveDelimiter(options);
      List<List<String>> rows = CsvSupport.parse(input.payload(), delimiter);
      if (withHeader(options) && !rows.isEmpty()) {
        rows = rows.subList(1, rows.size());
      }
      return Result.success(new ParseCsvOut(rows));
    } catch (RuntimeException e) {
      return Result.failure(new IllegalArgumentException("parseCsv: malformed CSV", e));
    }
  }

  private static char resolveDelimiter(CsvOptions options) {
    String delimiter = options != null ? options.delimiter() : null;
    if (delimiter == null || delimiter.isEmpty()) {
      return ',';
    }
    if (delimiter.length() != 1) {
      throw new IllegalArgumentException("parseCsv: delimiter must be exactly one character");
    }
    return delimiter.charAt(0);
  }

  private static boolean withHeader(CsvOptions options) {
    return options != null && options.withHeader() != null && options.withHeader();
  }
}
