package cbs.nova.dsl.compact;

import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Preprocesses model/POJO source files authored without a package declaration so that they can be
 * compiled into a shared target package alongside DSL sources.
 */
public final class ModelSourcePreprocessor {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+[^;]+;\\s*$");
  private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+.*;\\s*$");

  public record Result(@NonNull String className, @NonNull String preprocessedSource) {
  }

  public static @NonNull Result preprocess(
          @NonNull String fileName,
          @NonNull String rawSource,
          @NonNull String targetPackage) {
    if (targetPackage.isBlank()) {
      throw new IllegalArgumentException("targetPackage is required for model preprocessing");
    }
    if (PACKAGE_PATTERN.matcher(rawSource).find()) {
      throw new IllegalArgumentException(
              fileName + " must not declare a package; package is injected by the compiler");
    }
    var className = className(fileName);
    var split = splitImports(rawSource);
    var sb = new StringBuilder();
    sb.append("package ").append(targetPackage).append(";\n\n");
    if (!split.imports().isEmpty()) {
      sb.append(split.imports()).append("\n");
    }
    sb.append(split.body().strip()).append("\n");
    return new Result(className, sb.toString());
  }

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Model source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  private static SourceSplit splitImports(@NonNull String source) {
    var imports = new StringBuilder();
    var body = new StringBuilder();
    var matcher = IMPORT_PATTERN.matcher(source);
    var lastEnd = 0;
    while (matcher.find()) {
      imports.append(matcher.group().strip()).append("\n");
      if (matcher.start() > lastEnd) {
        body.append(source, lastEnd, matcher.start());
      }
      lastEnd = matcher.end();
    }
    body.append(source.substring(lastEnd));
    return new SourceSplit(imports.toString().stripTrailing(), body.toString());
  }

  private record SourceSplit(@NonNull String imports, @NonNull String body) {
  }
}
