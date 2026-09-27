package cbs.nova.dsl.builder.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FrontmatterNameParser {

  private static final String DELIMITER = "---";
  private static final Pattern NAME_LINE = Pattern.compile("^\\s*name\\s*:\\s*(.+?)\\s*$");

  private FrontmatterNameParser() {
  }

  static String name(String content) {
    if (content == null) {
      return null;
    }
    String[] lines = content.split("\\R", -1);
    if (lines.length == 0 || !DELIMITER.equals(lines[0].strip())) {
      return null;
    }
    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];
      if (DELIMITER.equals(line.strip())) {
        return null;
      }
      Matcher matcher = NAME_LINE.matcher(line);
      if (matcher.matches()) {
        return unquote(matcher.group(1));
      }
    }
    return null;
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
