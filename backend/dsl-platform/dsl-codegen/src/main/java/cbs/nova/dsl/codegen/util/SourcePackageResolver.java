package cbs.nova.dsl.codegen.util;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves the Java package where each DSL compact source and model source should be placed.
 *
 * <p>
 * DSL sources are placed in a versioned sub-package derived from the file name
 * ({@code basePackage.<fileNameSegment>.<versionSegment>}).
 *
 * <p>
 * Model sources are associated with the DSL file that imports them and placed in the same versioned
 * package. Models that are not imported by any DSL remain in the base package.
 */
@RequiredArgsConstructor
public final class SourcePackageResolver {

  private final DslPackageNameResolver packageNameResolver;

  public @NonNull Map<Path, String> resolveDslPackages(
          @NonNull List<Path> dslSources,
          String basePackage,
          String version,
          boolean useFileNameSubPackage) throws IOException {
    var packages = new HashMap<Path, String>();
    for (var source : dslSources) {
      var pkg = packageNameResolver.resolve(
              basePackage, version, source.getFileName().toString(), useFileNameSubPackage);
      packages.put(source, pkg);
    }
    return packages;
  }

  public @NonNull Map<String, String> resolveModelPackages(
          @NonNull List<Path> dslSources,
          @NonNull List<Path> modelSources,
          String basePackage,
          @NonNull Map<Path, String> dslPackages) throws IOException {
    var packages = new HashMap<String, String>();
    if (!hasPackage(basePackage)) {
      return packages;
    }
    var modelNames = modelClassNames(modelSources);
    for (var dslSource : dslSources) {
      var raw = Files.readString(dslSource);
      var dslPackage = dslPackages.get(dslSource);
      for (var importedModel : extractImportedModelClasses(raw, basePackage, modelNames)) {
        packages.putIfAbsent(importedModel, dslPackage);
      }
    }
    return packages;
  }

  public @NonNull Set<String> modelClassNames(@NonNull List<Path> modelSources) {
    return modelSources.stream()
            .map(p -> className(p.getFileName().toString()))
            .collect(Collectors.toSet());
  }

  public @NonNull String rewriteModelImports(
          @NonNull String source,
          String basePackage,
          @NonNull Map<String, String> modelPackages,
          @NonNull Set<String> modelClassNames) {
    if (!hasPackage(basePackage) || modelPackages.isEmpty() || modelClassNames.isEmpty()) {
      return source;
    }
    var escapedBase = Pattern.quote(basePackage);
    var classNames = modelClassNames.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    var pattern = Pattern.compile(
            "(?m)^(\\s*import\\s+)" + escapedBase + "\\.(" + classNames + ")(\\..*;\\s*)$");
    var matcher = pattern.matcher(source);
    var sb = new StringBuilder();
    while (matcher.find()) {
      var className = matcher.group(2);
      var newPackage = modelPackages.get(className);
      if (newPackage == null || newPackage.isBlank()) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
        continue;
      }
      matcher.appendReplacement(sb, Matcher.quoteReplacement(
              matcher.group(1) + newPackage + "." + className + matcher.group(3)));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static Set<String> extractImportedModelClasses(
          @NonNull String source,
          String basePackage,
          Set<String> modelClassNames) {
    if (!hasPackage(basePackage) || modelClassNames.isEmpty()) {
      return Set.of();
    }
    var escapedBase = Pattern.quote(basePackage);
    var classNames = modelClassNames.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    var pattern = Pattern.compile(
            "(?m)^\\s*import\\s+" + escapedBase + "\\.(" + classNames + ")\\..*;\\s*$");
    var result = new HashSet<String>();
    var matcher = pattern.matcher(source);
    while (matcher.find()) {
      result.add(matcher.group(1));
    }
    return result;
  }

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  private static boolean hasPackage(String targetPackage) {
    return targetPackage != null && !targetPackage.isBlank();
  }

}
