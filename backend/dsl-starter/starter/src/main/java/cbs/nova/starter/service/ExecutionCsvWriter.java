package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRun;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class ExecutionCsvWriter {

  public static final int DEFAULT_MAX_INPUT_OUTPUT_CHARS = 2000;

  private static final char DELIMITER = ',';
  private static final char QUOTE = '"';
  private static final String LINE_ENDING = "\r\n";
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
      return new Config(DEFAULT_MAX_INPUT_OUTPUT_CHARS);
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

  private void writeRun(Appendable out, DslRun run, Config config) throws IOException {
    writeField(out, run.runId());
    out.append(DELIMITER);
    writeField(out, run.processName());
    out.append(DELIMITER);
    writeField(out, run.status());
    out.append(DELIMITER);
    writeField(out, effectiveMode(run.executionMode()));
    out.append(DELIMITER);
    writeField(out, run.triggeredBy());
    out.append(DELIMITER);
    writeField(out, run.correlationId());
    out.append(DELIMITER);
    writeField(out, run.startedAt().toString());
    out.append(DELIMITER);
    writeField(out, run.finishedAt() != null ? run.finishedAt().toString() : null);
    out.append(DELIMITER);
    writeField(out, duration(run));
    out.append(DELIMITER);
    writeField(out, firstLine(run.error()));
    out.append(DELIMITER);
    writeField(out, truncate(run.input(), config.maxInputOutputChars()));
    out.append(DELIMITER);
    writeField(out, truncate(run.output(), config.maxInputOutputChars()));
    out.append(LINE_ENDING);
  }

  private void writeRow(Appendable out, String[] fields) throws IOException {
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        out.append(DELIMITER);
      }
      writeField(out, fields[i]);
    }
    out.append(LINE_ENDING);
  }

  private void writeField(Appendable out, String value) throws IOException {
    if (value == null) {
      return;
    }
    if (needsQuoting(value)) {
      out.append(QUOTE);
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c == QUOTE) {
          out.append(QUOTE);
        }
        out.append(c);
      }
      out.append(QUOTE);
    } else {
      out.append(value);
    }
  }

  private static boolean needsQuoting(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == DELIMITER || c == QUOTE || c == '\r' || c == '\n') {
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
