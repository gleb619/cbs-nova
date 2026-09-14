package cbs.nova.dsl.codegen.task;

import static cbs.nova.dsl.config.Constants.JAVA_FILE_SUFFIX;

import cbs.nova.dsl.codegen.CompilerConstants;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.preprocessor.DslPreprocessor;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.codegen.util.SourcePackageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

@Slf4j
@RequiredArgsConstructor
public final class PreprocessSourcesTask implements CompileTask {

  private final DslPreprocessor dslPreprocessor;
  private final CodegenNaming codegenNaming;
  private final SourcePackageResolver sourcePackageResolver;

  @Override
  public String name() {
    return "preprocess";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var options = context.options();
    var dslDir = options.srcDir().resolve(CompilerConstants.DSL_FOLDER);
    if (!Files.isDirectory(dslDir)) {
      return context.toBuilder().preprocessedSources(List.of()).build();
    }
    List<Path> files;
    try (var stream = Files.walk(dslDir)) {
      files = stream.filter(file -> file.toString().endsWith(JAVA_FILE_SUFFIX)).toList();
    }
    var modelsDir = options.srcDir().resolve(CompilerConstants.MODELS_FOLDER);
    List<Path> modelFiles;
    if (Files.isDirectory(modelsDir)) {
      try (var stream = Files.walk(modelsDir)) {
        modelFiles = stream.filter(file -> file.toString().endsWith(JAVA_FILE_SUFFIX)).toList();
      }
    } else {
      modelFiles = List.of();
    }

    var dslPackages = sourcePackageResolver.resolveDslPackages(
            files, options.targetPackage(), options.buildVersion(),
            options.useFileNameSubPackage());
    var modelPackages = sourcePackageResolver.resolveModelPackages(
            files, modelFiles, options.targetPackage(), options.buildVersion(), dslPackages);
    var modelClassNames = sourcePackageResolver.modelClassNames(modelFiles);

    final var snapshot = context;
    var resolver = new DslPackageNameResolver(codegenNaming);
    var results = VirtualThreads.runAll(files.stream()
            .<Callable<@Nullable String>>map(file -> () -> preprocess(
                    file, resolver, snapshot, modelPackages, modelClassNames))
            .toList());
    return context.toBuilder()
            .preprocessedSources(results.stream().filter(Objects::nonNull).toList())
            .build();
  }

  private @Nullable String preprocess(
          Path file,
          DslPackageNameResolver resolver,
          CompileContext context,
          Map<String, String> modelPackages,
          Set<String> modelClassNames) throws IOException {
    var options = context.options();
    try {
      var fileName = file.getFileName().toString();
      var rawSource = Files.readString(file);
      var packageName = resolver.resolve(
              options.targetPackage(),
              options.buildVersion(),
              fileName,
              options.useFileNameSubPackage());
      var rewritten = sourcePackageResolver.rewriteModelImports(
              rawSource, options.targetPackage(), options.buildVersion(), modelPackages,
              modelClassNames);
      var preprocess = dslPreprocessor.preprocess(fileName, rewritten, packageName);
      return preprocess.preprocessedSource();
    } catch (IllegalArgumentException e) {
      log.atLevel(Level.WARN).log(
              () -> "[DslCompiler] Skipping invalid DSL source %s: %s".formatted(file,
                      e.getMessage()));
      return null;
    }
  }
}
