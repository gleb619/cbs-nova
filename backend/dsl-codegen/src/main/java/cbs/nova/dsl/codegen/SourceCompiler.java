package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.codegen.generator.DefinitionProviderGenerator;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.compact.ModelSourcePreprocessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public final class SourceCompiler {

  private final Level logLevel;
  private final DefinitionProviderGenerator definitionProviderGenerator;
  private final CodeWriter codeWriter;

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path srcDir,
          @NonNull Path outputDir,
          @NonNull JavaCompiler compiler,
          CompileOptions options) throws IOException {
    codeWriter.createDirectories(outputDir);

    var dslDir = srcDir.resolve(CompilerConstants.DSL_FOLDER);
    var modelsDir = srcDir.resolve(CompilerConstants.MODELS_FOLDER);
    var classpath = System.getProperty("java.class.path");

    var dslSources = collectJavaSources(dslDir);
    var modelSources = collectJavaSources(modelsDir);

    if (dslSources.isEmpty() && modelSources.isEmpty()) {
      log.atLevel(logLevel).log(
              () -> "[SourceCompiler] No .java sources found under %s (expected dsl/ and models/)"
                      .formatted(srcDir));
      return List.of();
    }
    if (dslSources.isEmpty()) {
      log.atLevel(logLevel)
              .log(() -> "[SourceCompiler] No DSL sources found under %s".formatted(dslDir));
      return List.of();
    }

    var targetPackage = (options != null) ? options.targetPackage() : null;

    var dslResults = preprocessInVirtualThreads(dslSources, targetPackage, true);
    if (dslResults.isEmpty()) {
      log.atLevel(logLevel)
              .log(() -> "[SourceCompiler] No valid compact DSL sources found under %s"
                      .formatted(dslDir));
      return List.of();
    }

    var modelResults = preprocessInVirtualThreads(modelSources, targetPackage, false);

    var preprocessedModels = writePreprocessedSources(modelResults, outputDir, targetPackage);
    var preprocessedDsl = writePreprocessedSources(dslResults, outputDir, targetPackage);

    var allSources = new ArrayList<Path>();
    for (var s : preprocessedModels) {
      allSources.add(s.sourceFile());
    }
    for (var s : preprocessedDsl) {
      allSources.add(s.sourceFile());
    }

    if (!compileSources(compiler, classpath, allSources, outputDir)) {
      throw new IllegalStateException("[SourceCompiler] Failed to compile DSL/model sources");
    }

    var compiledClassNames = new ArrayList<String>();
    for (var source : preprocessedDsl) {
      compiledClassNames.add(qualifiedClassName(source.className(), targetPackage));
    }

    var providerFqcn = definitionProviderGenerator.generate(
            outputDir, compiledClassNames, targetPackage);
    var providerSource = outputDir.resolve(
            parsePackage(targetPackage));
    compileProvider(compiler, classpath, providerSource, outputDir);

    return loadDefinitions(outputDir, providerFqcn);
  }

  private List<Path> collectJavaSources(@NonNull Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
              .filter(p -> p.toString().endsWith(".java"))
              .toList();
    }
  }

  private @NonNull List<PreprocessResult> preprocessInVirtualThreads(
          @NonNull List<Path> sources,
          String targetPackage,
          boolean dsl) throws IOException {
    if (sources.isEmpty()) {
      return List.of();
    }
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<PreprocessResult>>();
      for (var file : sources) {
        Callable<PreprocessResult> task = dsl
                ? () -> preprocessDsl(file, targetPackage)
                : () -> preprocessModel(file, targetPackage);
        futures.add(executor.submit(task));
      }
      var results = new ArrayList<PreprocessResult>();
      for (var future : futures) {
        try {
          var result = future.get();
          if (result != null) {
            results.add(result);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("[SourceCompiler] Preprocessing interrupted", e);
        } catch (ExecutionException e) {
          var cause = e.getCause();
          if (cause instanceof IOException io) {
            throw io;
          }
          if (cause instanceof RuntimeException re) {
            throw re;
          }
          throw new IllegalStateException("[SourceCompiler] Preprocessing failed", cause);
        }
      }
      return results;
    }
  }

  private @NonNull List<PreprocessedSource> writePreprocessedSources(
          @NonNull List<PreprocessResult> results,
          @NonNull Path outputDir,
          String targetPackage) throws IOException {
    var written = new ArrayList<PreprocessedSource>();
    for (var result : results) {
      var outputFile = (targetPackage != null && !targetPackage.isBlank())
              ? outputDir.resolve(targetPackage.replace('.', '/')).resolve(result.fileName())
              : outputDir.resolve(result.fileName());
      codeWriter.write(outputFile, result.source());
      log.atLevel(logLevel).log(() -> "[SourceCompiler] Preprocessed %s -> %s"
              .formatted(result.fileName(), outputFile));
      written.add(new PreprocessedSource(result.className(), outputFile));
    }
    return written;
  }

  private PreprocessResult preprocessDsl(
          @NonNull Path sourceFile,
          String targetPackage) throws IOException {
    var fileName = sourceFile.getFileName().toString();
    var rawSource = Files.readString(sourceFile);
    try {
      var result = CompactSourcePreprocessor.preprocess(fileName, rawSource, targetPackage);
      log.atLevel(logLevel)
              .log(() -> "[SourceCompiler] Preprocessed DSL %s".formatted(sourceFile));
      return new PreprocessResult(result.className(), fileName, result.preprocessedSource());
    } catch (IllegalArgumentException e) {
      log.atLevel(logLevel).log(() -> "[SourceCompiler] %s".formatted(e.getMessage()));
      return null;
    }
  }

  private PreprocessResult preprocessModel(
          @NonNull Path sourceFile,
          String targetPackage) throws IOException {
    var fileName = sourceFile.getFileName().toString();
    var rawSource = Files.readString(sourceFile);
    try {
      if (targetPackage == null || targetPackage.isBlank()) {
        throw new IllegalArgumentException(
                "targetPackage is required to preprocess model " + fileName);
      }
      var result = ModelSourcePreprocessor.preprocess(fileName, rawSource, targetPackage);
      log.atLevel(logLevel)
              .log(() -> "[SourceCompiler] Preprocessed model %s".formatted(sourceFile));
      return new PreprocessResult(result.className(), fileName, result.preprocessedSource());
    } catch (IllegalArgumentException e) {
      log.atLevel(logLevel).log(() -> "[SourceCompiler] %s".formatted(e.getMessage()));
      return null;
    }
  }

  private boolean compileSources(
          @NonNull JavaCompiler compiler,
          @NonNull String classpath,
          @NonNull List<Path> sourceFiles,
          @NonNull Path outputDir) {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      var units = fm.getJavaFileObjectsFromFiles(sourceFiles.stream()
              .map(Path::toFile)
              .toList());
      var task = compiler.getTask(null, fm, diagnostics, options, null, units);
      if (!task.call()) {
        diagnostics.getDiagnostics().forEach(d -> log.atLevel(logLevel)
                .log(() -> {
                  String fileName = d.getSource().getName();
                  long lineNumber = d.getLineNumber();
                  return "[SourceCompiler] %s#L%s compilation: %s".formatted(
                          fileName,
                          lineNumber,
                          d.getMessage(null));
                }));
        return false;
      }
      return true;
    } catch (IOException e) {
      log.atLevel(logLevel).setCause(e)
              .log(() -> "[SourceCompiler] Compilation failed: %s".formatted(e.getMessage()));
      return false;
    }
  }

  private void compileProvider(
          @NonNull JavaCompiler compiler,
          @NonNull String classpath,
          @NonNull Path providerSource,
          @NonNull Path outputDir) {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var providerClasspath = classpath + File.pathSeparator + outputDir;
      var options = List.of("-classpath", providerClasspath, "-d", outputDir.toString());
      var unit = fm.getJavaFileObjectsFromFiles(List.of(providerSource.toFile()));
      var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
      if (!task.call()) {
        diagnostics.getDiagnostics().forEach(
                d -> log.atLevel(logLevel)
                        .log(() -> {
                          String fileName = d.getSource().getName();
                          long lineNumber = d.getLineNumber();

                          return "[SourceCompiler] %s#L%s provider: %s".formatted(
                                  fileName,
                                  lineNumber,
                                  d.getMessage(null));
                        }));
        throw new IllegalStateException("Failed to compile generated DSL definition provider");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to compile generated DSL definition provider", e);
    }
  }

  private @NonNull String qualifiedClassName(
          @NonNull String className,
          String targetPackage) {
    return (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage + "." + className
            : className;
  }

  @Deprecated(forRemoval = true)
  private @NonNull List<DslObject> loadDefinitions(
          @NonNull Path outputDir,
          @NonNull String providerFqcn) {
    URL url;
    try {
      url = outputDir.toUri().toURL();
    } catch (IOException e) {
      log.atLevel(logLevel).setCause(e).log(
              () -> "[SourceCompiler] Failed to build output URL: %s".formatted(e.getMessage()));
      return List.of();
    }
    var loader = new URLClassLoader(new URL[]{url},
            Thread.currentThread().getContextClassLoader());
    try {
      var clazz = loader.loadClass(providerFqcn);
      // TODO: remove reflection, use typed info
      var provider = (DslDefinitionProvider) clazz.getDeclaredConstructor().newInstance();
      return provider.definitions();
    } catch (Exception e) {
      log.atLevel(logLevel).setCause(e)
              .log(() -> "[SourceCompiler] Failed to load DSL definitions: %s"
                      .formatted(e.getMessage()));
    }
    return List.of();
  }

  private String parsePackage(String targetPackage) {
    if (targetPackage != null && !targetPackage.isBlank()) {
      String packagePath = targetPackage.replace('.', '/');
      return "%s/%s.java".formatted(packagePath, DefinitionProviderGenerator.PROVIDER_CLASS);
    }

    return DefinitionProviderGenerator.PROVIDER_CLASS + ".java";
  }

  /* ============= */

  public record CompileOptions(
          String buildVersion,
          String targetPackage,
          Level logLevel) {
  }

  private record PreprocessedSource(@NonNull String className, @NonNull Path sourceFile) {
  }

  private record PreprocessResult(@NonNull String className, @NonNull String fileName,
          @NonNull String source) {
  }

}
