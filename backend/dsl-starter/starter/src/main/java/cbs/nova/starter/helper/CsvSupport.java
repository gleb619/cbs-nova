package cbs.nova.starter.helper;

import static cbs.nova.starter.core.StarterConstants.CSV_QUOTE;

import cbs.nova.starter.core.StarterConstants;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class CsvSupport {

  static List<List<String>> parse(String input, char delimiter) {
    List<List<String>> rows = new ArrayList<>();
    List<String> currentRow = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    State state = State.START;

    int len = input.length();
    for (int i = 0; i < len; i++) {
      char c = input.charAt(i);
      switch (state) {
        case START -> {
          if (c == CSV_QUOTE) {
            state = State.QUOTED;
          } else if (c == delimiter) {
            currentRow.add(field.toString());
            field.setLength(0);
          } else if (c == '\r' || c == '\n') {
            flushRow(currentRow, field, rows);
            if (c == '\r' && i + 1 < len && input.charAt(i + 1) == '\n') {
              i++;
            }
          } else {
            field.append(c);
            state = State.UNQUOTED;
          }
        }
        case UNQUOTED -> {
          if (c == delimiter) {
            currentRow.add(field.toString());
            field.setLength(0);
            state = State.START;
          } else if (c == '\r' || c == '\n') {
            flushRow(currentRow, field, rows);
            state = State.START;
            if (c == '\r' && i + 1 < len && input.charAt(i + 1) == '\n') {
              i++;
            }
          } else if (c == CSV_QUOTE) {
            throw new IllegalArgumentException("csv: unexpected quote in unquoted field");
          } else {
            field.append(c);
          }
        }
        case QUOTED -> {
          if (c == CSV_QUOTE) {
            if (i + 1 < len && input.charAt(i + 1) == CSV_QUOTE) {
              field.append(CSV_QUOTE);
              i++;
            } else {
              state = State.AFTER_QUOTE;
            }
          } else {
            field.append(c);
          }
        }
        case AFTER_QUOTE -> {
          if (c == delimiter) {
            currentRow.add(field.toString());
            field.setLength(0);
            state = State.START;
          } else if (c == '\r' || c == '\n') {
            flushRow(currentRow, field, rows);
            state = State.START;
            if (c == '\r' && i + 1 < len && input.charAt(i + 1) == '\n') {
              i++;
            }
          } else {
            throw new IllegalArgumentException("csv: unexpected character after closing quote");
          }
        }
      }
    }

    if (state == State.QUOTED) {
      throw new IllegalArgumentException("csv: unterminated quoted field");
    }
    if (state != State.START || !currentRow.isEmpty() || field.length() > 0) {
      currentRow.add(field.toString());
      rows.add(List.copyOf(currentRow));
    }
    return rows;
  }

  static String format(List<List<String>> rows, char delimiter, String lineSeparator) {
    StringBuilder out = new StringBuilder();
    for (List<String> row : rows) {
      for (int i = 0; i < row.size(); i++) {
        if (i > 0) {
          out.append(delimiter);
        }
        out.append(escape(row.get(i), delimiter));
      }
      out.append(lineSeparator);
    }
    return out.toString();
  }

  private static void flushRow(List<String> row, StringBuilder field, List<List<String>> rows) {
    row.add(field.toString());
    rows.add(List.copyOf(row));
    row.clear();
    field.setLength(0);
  }

  private static String escape(String value, char delimiter) {
    if (value == null) {
      value = "";
    }
    boolean needsQuoting = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == delimiter || c == CSV_QUOTE || c == '\r' || c == '\n') {
        needsQuoting = true;
        break;
      }
    }
    if (!needsQuoting) {
      return value;
    }
    StringBuilder quoted = new StringBuilder(value.length() + 2);
    quoted.append(CSV_QUOTE);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == CSV_QUOTE) {
        quoted.append(CSV_QUOTE);
      }
      quoted.append(c);
    }
    quoted.append(CSV_QUOTE);
    return quoted.toString();
  }

  private enum State {
    START, UNQUOTED, QUOTED, AFTER_QUOTE
  }
}
