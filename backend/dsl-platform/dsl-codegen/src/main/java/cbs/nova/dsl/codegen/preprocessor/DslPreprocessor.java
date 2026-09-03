package cbs.nova.dsl.codegen.preprocessor;

import cbs.nova.dsl.DslCompactSource;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DslPreprocessor {

  private static final List<String> DEFAULT_IMPORTS = List.of(
          "import cbs.nova.dsl.*;",
          "import cbs.nova.dsl.model.*;",
          "import java.time.*;",
          "import java.util.*;",
          "import java.util.stream.*;",
          "import " + DslCompactSource.class.getName() + ";");
  private static final Pattern DEFINE_PATTERN = Pattern.compile(
          "(?m)^(\\s*)(?:public\\s+)?(?:java\\.util\\.)?List\\s*<\\s*(?:cbs\\.nova\\.dsl\\.)?DslObject\\s*>\\s+define\\s*\\(\\s*\\)\\s*\\{");
  private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+");
  private static final Pattern TOP_LEVEL_TYPE_PATTERN = Pattern.compile(
          "(?m)^\\s*(?:public\\s+)?(?:class|interface|enum|record)\\s+");
  private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+.*;\\s*$");

  public @NonNull Result preprocess(
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
    return new Result(className,
            wrapInClass(className, fileName, split.imports(), body, targetPackage));
  }

  public boolean isValidCompactSource(@NonNull String source) {
    if (PACKAGE_PATTERN.matcher(source).find()) {
      return false;
    }
    if (TOP_LEVEL_TYPE_PATTERN.matcher(source).find()) {
      return false;
    }
    return DEFINE_PATTERN.matcher(source).find();
  }

  private @NonNull String validationErrors(@NonNull String source) {
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

  private @NonNull String ensurePublicDefine(@NonNull String source) {
    return DEFINE_PATTERN.matcher(source)
            .replaceAll(mr -> mr.group(1) + "public @Override List<DslObject> define() {");
  }

  private @NonNull String wrapInClass(
          @NonNull String className,
          @NonNull String fileName,
          @NonNull String imports,
          @NonNull String body,
          String targetPackage) {
    //TODO: redo from StringBuilder to textblock
    var sb = new StringBuilder();
    if (targetPackage != null && !targetPackage.isBlank()) {
      sb.append("package ").append(targetPackage).append(";\n\n");
    }
    for (var defaultImport : DEFAULT_IMPORTS) {
      if (!imports.contains(defaultImport)) {
        sb.append(defaultImport).append("\n");
      }
    }
    if (!imports.isEmpty()) {
      sb.append(imports).append("\n\n");
    } else {
      sb.append("\n");
    }
    sb.append("public class ").append(className)
            .append(" implements ").append(DslCompactSource.class.getSimpleName()).append(" {\n")
            .append(body).append("\n")
            .append(filenameMethod(fileName))
            .append("}\n");
    return sb.toString();
  }

  private SourceSplit splitImports(@NonNull String source) {
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

  private @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("DSL source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  private String filenameMethod(String fileName) {
    return //language=java
        """
        @Override
          public String filename() {
            return "%s";
          }
        """.formatted(fileName);
  }

  private record SourceSplit(@NonNull String imports, @NonNull String body) {
  }

  public record Result(@NonNull String className, @NonNull String preprocessedSource) {
  }
}
