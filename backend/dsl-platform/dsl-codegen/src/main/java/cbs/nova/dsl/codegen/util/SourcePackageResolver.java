package cbs.nova.dsl.codegen.util;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class SourcePackageResolver {

  private final DslPackageNameResolver packageNameResolver;
  private final ModelImportResolver modelImportResolver;

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
          String version,
          @NonNull Map<Path, String> dslPackages) throws IOException {
    var packages = new HashMap<String, String>();
    var modelNames = modelClassNames(modelSources);
    if (modelNames.isEmpty()) {
      return packages;
    }
    for (var dslSource : dslSources) {
      var raw = Files.readString(dslSource);
      var dslPackage = dslPackages.get(dslSource);
      for (var importedModel : modelImportResolver.extract(raw, basePackage, version, modelNames)) {
        packages.putIfAbsent(importedModel.modelClass(), dslPackage);
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
          String version,
          @NonNull Map<String, String> modelPackages,
          @NonNull Set<String> modelClassNames) {
    if (modelPackages.isEmpty() || modelClassNames.isEmpty()) {
      return source;
    }
    return modelImportResolver.rewrite(
            source, basePackage, version, modelPackages, modelClassNames);
  }

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

}
