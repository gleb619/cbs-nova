package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class CodeGenUtil {

  private CodeGenUtil() {}

  public static String currentTimestamp() {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
  }

  public static String toClassName(String code) {
    StringBuilder sb = new StringBuilder();
    boolean upper = true;
    for (char c : code.toCharArray()) {
      if (c == '_' || c == '-') {
        upper = true;
      } else if (upper) {
        sb.append(Character.toUpperCase(c));
        upper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  public static String toCamelCase(String input) {
    StringBuilder sb = new StringBuilder();
    boolean upper = true;
    for (char c : input.toCharArray()) {
      if (c == '_' || c == '-' || c == '.') {
        upper = true;
      } else if (Character.isLetterOrDigit(c)) {
        sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upper = false;
      }
    }
    return sb.toString();
  }

  public static String sanitizeDslFileName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    return toCamelCase(base);
  }

  public static String toCamelCasePreservingFirstUpper(String input) {
    StringBuilder sb = new StringBuilder();
    boolean upper = false;
    for (char c : input.toCharArray()) {
      if (c == '_' || c == '-' || c == '.') {
        upper = true;
      } else if (Character.isLetterOrDigit(c)) {
        sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upper = false;
      }
    }
    return sb.toString();
  }

  public static String toFieldName(String code) {
    String className = toClassName(code);
    return Character.toLowerCase(className.charAt(0)) + className.substring(1);
  }

  public static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }

  public static void writeToFiler(Filer filer, String fqcn, String content) throws IOException {
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(content);
    }
  }
}
