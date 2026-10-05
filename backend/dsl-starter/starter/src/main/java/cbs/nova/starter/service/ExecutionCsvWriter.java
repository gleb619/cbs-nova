package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.CSV_DELIMITER;
import static cbs.nova.starter.core.StarterConstants.CSV_LINE_ENDING;
import static cbs.nova.starter.core.StarterConstants.CSV_QUOTE;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.starter.core.StarterConstants;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class ExecutionCsvWriter {

  private static final String[] HEADER = {
      "runId", "processName", "status", "mode", "triggeredBy", "correlationId",
      "startedAt", "finishedAt", "duration", "error", "input", "output"
  };

  public record Config(int maxInputOutputChars) {
    public Config {
      if (maxInputOutputChars < 0) {
        throw new IllegalArgumentException("maxInputOutputChars must not be negative");
      }
    }

    public static Config defaults() {
      return new Config(StarterConstants.DEFAULT_MAX_INPUT_OUTPUT_CHARS);
    }
  }

  public void writeCsv(List<DslRun> runs, Appendable out) throws IOException {
    writeCsv(runs, out, Config.defaults());
  }

  public void writeCsv(List<DslRun> runs, Appendable out, Config config) throws IOException {
    writeRow(out, HEADER);
    for (DslRun run : runs) {
      writeRun(out, run, config);
    }
  }

  private static final char[] FORMULA_TRIGGER_CHARS = {'=', '+', '-', '@', '\t', '\r'};

  private void writeRun(Appendable out, DslRun run, Config config) throws IOException {
    // Per-column neutralization decision:
    // - runId, processName, status, mode, triggeredBy, correlationId, error, input, output
    // are caller/DSL-controlled free-text => prefix ' when the first char is a formula
    // trigger (=, +, -, @, tab, CR) to mitigate CSV/formula injection (OWASP).
    // - startedAt/finishedAt/duration are machine-generated ISO-8601 / integer seconds;
    // leaving them untouched keeps '-' (timezone offset, negative seconds) intact.
    writeField(out, run.runId(), true);
    out.append(CSV_DELIMITER);
    writeField(out, run.processName(), true);
    out.append(CSV_DELIMITER);
    writeField(out, run.status(), true);
    out.append(CSV_DELIMITER);
    writeField(out, effectiveMode(run.executionMode()), true);
    out.append(CSV_DELIMITER);
    writeField(out, run.triggeredBy(), true);
    out.append(CSV_DELIMITER);
    writeField(out, run.correlationId(), true);
    out.append(CSV_DELIMITER);
    writeField(out, run.startedAt().toString(), false);
    out.append(CSV_DELIMITER);
    writeField(out, run.finishedAt() != null ? run.finishedAt().toString() : null, false);
    out.append(CSV_DELIMITER);
    writeField(out, duration(run), false);
    out.append(CSV_DELIMITER);
    writeField(out, firstLine(run.error()), true);
    out.append(CSV_DELIMITER);
    writeField(out, truncate(run.input(), config.maxInputOutputChars()), true);
    out.append(CSV_DELIMITER);
    writeField(out, truncate(run.output(), config.maxInputOutputChars()), true);
    out.append(CSV_LINE_ENDING);
  }

  private void writeRow(Appendable out, String[] fields) throws IOException {
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        out.append(CSV_DELIMITER);
      }
      writeField(out, fields[i]);
    }
    out.append(CSV_LINE_ENDING);
  }

  private void writeField(Appendable out, String value) throws IOException {
    writeField(out, value, true);
  }

  /**
   * Writes a CSV cell. When {@code neutralizeFormulas} is true and {@code value} starts with one of
   * {@code = + - @ \t \r}, a leading single quote is prepended to disarm formula execution in
   * spreadsheet apps. Neutralization is applied <em>before</em> quote/escape processing so embedded
   * quotes inside the value are still doubled correctly.
   */
  private void writeField(Appendable out, String value, boolean neutralizeFormulas)
          throws IOException {
    if (value == null) {
      return;
    }
    String effective = neutralizeFormulas ? neutralizeFormula(value) : value;
    if (needsQuoting(effective)) {
      out.append(CSV_QUOTE);
      for (int i = 0; i < effective.length(); i++) {
        char c = effective.charAt(i);
        if (c == CSV_QUOTE) {
          out.append(CSV_QUOTE);
        }
        out.append(c);
      }
      out.append(CSV_QUOTE);
    } else {
      out.append(effective);
    }
  }

  static String neutralizeFormula(String value) {
    if (value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    for (char trigger : FORMULA_TRIGGER_CHARS) {
      if (first == trigger) {
        return "'" + value;
      }
    }
    return value;
  }

  private static boolean needsQuoting(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == CSV_DELIMITER || c == CSV_QUOTE || c == '\r' || c == '\n') {
        return true;
      }
    }
    return false;
  }

  private static String effectiveMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return "RUN";
    }
    return mode.toUpperCase(Locale.ROOT);
  }

  private static String duration(DslRun run) {
    if (run.finishedAt() == null) {
      return null;
    }
    return String.valueOf(Duration.between(run.startedAt(), run.finishedAt()).getSeconds());
  }

  private static String firstLine(String error) {
    if (error == null) {
      return null;
    }
    int end = error.indexOf('\n');
    if (end == -1) {
      end = error.indexOf('\r');
    }
    return end == -1 ? error : error.substring(0, end);
  }

  private static String truncate(String value, int maxChars) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars);
  }
}
