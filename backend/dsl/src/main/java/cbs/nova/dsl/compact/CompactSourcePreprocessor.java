package cbs.nova.dsl.compact;

import cbs.nova.dsl.DslCompactSource;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Turns a compact DSL source file (no package/class declaration, one {@code define()} method) into
 * a normal Java class that implements {@link DslCompactSource}.
 */
public final class CompactSourcePreprocessor {

  private static final String COMPACT_SOURCE_INTERFACE = DslCompactSource.class.getName();

  private static final Pattern DEFINE_PATTERN = Pattern.compile(
          "(?m)^(\\s*)(?:public\\s+)?(?:java\\.util\\.)?List\\s*<\\s*(?:cbs\\.nova\\.dsl\\.)?DslObject\\s*>\\s+define\\s*\\(\\s*\\)\\s*\\{");
  private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+");
  private static final Pattern TOP_LEVEL_TYPE_PATTERN = Pattern.compile(
          "(?m)^\\s*(?:public\\s+)?(?:class|interface|enum|record)\\s+");
  private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+.*;\\s*$");

  public record Result(@NonNull String className, @NonNull String preprocessedSource) {
  }

  /**
   * Validates and preprocesses the given compact source without a target package.
   */
  public static @NonNull Result preprocess(@NonNull String fileName, @NonNull String rawSource) {
    return preprocess(fileName, rawSource, null);
  }

  /**
   * Validates and preprocesses the given compact source, optionally injecting a target package.
   *
   * @param targetPackage
   *          optional package to inject; if null/blank the compiled class stays in the default
   *          package
   */
  public static @NonNull Result preprocess(
          @NonNull String fileName,
          @NonNull String rawSource,
          String targetPackage) {
    if (!isValidCompactSource(rawSource)) {
      throw new IllegalArgumentException(
              fileName + " is not a valid compact DSL source: " + validationErrors(rawSource));
    }
    var className = className(fileName);
    var split = splitImports(rawSource);
    var body = ensurePublicDefine(split.body());
    return new Result(className, wrapInClass(className, split.imports(), body, targetPackage));
  }

  public static boolean isValidCompactSource(@NonNull String source) {
    if (PACKAGE_PATTERN.matcher(source).find()) {
      return false;
    }
    if (TOP_LEVEL_TYPE_PATTERN.matcher(source).find()) {
      return false;
    }
    return DEFINE_PATTERN.matcher(source).find();
  }

  private static @NonNull String validationErrors(@NonNull String source) {
    var errors = new ArrayList<String>();
    if (PACKAGE_PATTERN.matcher(source).find()) {
      errors.add("must not declare a package");
    }
    if (TOP_LEVEL_TYPE_PATTERN.matcher(source).find()) {
      errors.add("must not declare a top-level class/interface/enum/record");
    }
    if (!DEFINE_PATTERN.matcher(source).find()) {
      errors.add("must declare a List<DslObject> define() method");
    }
    return String.join(", ", errors);
  }

  private static @NonNull String ensurePublicDefine(@NonNull String source) {
    return DEFINE_PATTERN.matcher(source)
            .replaceAll(mr -> mr.group(1) + "public @Override List<DslObject> define() {");
  }

  private static @NonNull String wrapInClass(
          @NonNull String className,
          @NonNull String imports,
          @NonNull String body,
          String targetPackage) {
    var sb = new StringBuilder();
    if (targetPackage != null && !targetPackage.isBlank()) {
      sb.append("package ").append(targetPackage).append(";\n\n");
    }
    if (!imports.isEmpty()) {
      sb.append(imports).append("\n");
    }
    sb.append("public class ").append(className)
            .append(" implements ").append(COMPACT_SOURCE_INTERFACE).append(" {\n")
            .append(body).append("\n")
            .append("}\n");
    return sb.toString();
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

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("DSL source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  private record SourceSplit(@NonNull String imports, @NonNull String body) {
  }
}
