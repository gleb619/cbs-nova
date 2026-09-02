package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.codegen.generator.DefinitionProviderGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.util.SourcePackageResolver;
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
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public final class SourceCompiler {

  private final DefinitionProviderGenerator definitionProviderGenerator;
  private final CodeWriter codeWriter;
  private final CodegenNaming codegenNaming;

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path srcDir,
          @NonNull Path outputDir,
          @NonNull JavaCompiler compiler,
          CompileOptions options) throws IOException {
    codeWriter.createDirectories(outputDir);

    var dslDir = srcDir.resolve(CompilerConstants.DSL_FOLDER);
    var modelsDir = srcDir.resolve(CompilerConstants.MODELS_FOLDER);
    var classpath = resolveClasspath(options);

    var dslSources = collectJavaSources(dslDir);
    var modelSources = collectJavaSources(modelsDir);

    if (dslSources.isEmpty() && modelSources.isEmpty()) {
      log.atLevel(Level.DEBUG).log(
              () -> "[SourceCompiler] No .java sources found under %s (expected dsl/ and models/)"
                      .formatted(srcDir));
      return List.of();
    }
    if (dslSources.isEmpty()) {
      log.atLevel(Level.DEBUG)
              .log(() -> "[SourceCompiler] No DSL sources found under %s".formatted(dslDir));
      return List.of();
    }

    var basePackage = (options != null) ? options.targetPackage() : null;
    var version = (options != null) ? options.buildVersion() : null;
    var useFileNameSubPackage = (options != null) && options.useFileNameSubPackage();
    var packageResolver = new SourcePackageResolver(codegenNaming);

    var dslPackages = packageResolver.resolveDslPackages(
            dslSources, basePackage, version, useFileNameSubPackage);
    var modelPackages = packageResolver.resolveModelPackages(
            dslSources, modelSources, basePackage, dslPackages);
    var modelClassNames = packageResolver.modelClassNames(modelSources);

    var dslResults = preprocessDslSources(dslSources, dslPackages, basePackage, modelPackages,
            modelClassNames, packageResolver);
    if (dslResults.isEmpty()) {
      log.atLevel(Level.DEBUG)
              .log(() -> "[SourceCompiler] No valid compact DSL sources found under %s"
                      .formatted(dslDir));
      return List.of();
    }

    var modelResults = preprocessModelSources(modelSources, modelPackages, basePackage,
            modelClassNames, packageResolver);

    var preprocessedDsl = writePreprocessedSources(dslResults, outputDir);
    var preprocessedModels = writePreprocessedSources(modelResults, outputDir);

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
      compiledClassNames.add(qualifiedClassName(source.className(), source.targetPackage()));
    }

    var providerFqcn = definitionProviderGenerator.generate(
            outputDir, compiledClassNames, basePackage);
    var providerSource = outputDir.resolve(
            parsePackage(basePackage));
    compileProvider(compiler, classpath, providerSource, outputDir);

    return loadDefinitions(outputDir, providerFqcn);
  }

  private List<PreprocessResult> preprocessDslSources(
          List<Path> dslSources,
          Map<Path, String> dslPackages,
          String basePackage,
          Map<String, String> modelPackages,
          Set<String> modelClassNames,
          SourcePackageResolver packageResolver) throws IOException {
    var tasks = new ArrayList<Callable<PreprocessResult>>();
    for (var source : dslSources) {
      tasks.add(() -> {
        var fileName = source.getFileName().toString();
        try {
          var raw = Files.readString(source);
          var targetPackage = dslPackages.get(source);
          var rewritten = packageResolver.rewriteModelImports(
                  raw, basePackage, modelPackages, modelClassNames);
          var result = CompactSourcePreprocessor.preprocess(fileName, rewritten, targetPackage);
          return new PreprocessResult(
                  result.className(), fileName, result.preprocessedSource(), targetPackage);
        } catch (IllegalArgumentException e) {
          log.atLevel(Level.DEBUG).log(() -> "[SourceCompiler] %s".formatted(e.getMessage()));
          return null;
        }
      });
    }
    return runPreprocessTasks(tasks, "DSL");
  }

  private List<PreprocessResult> preprocessModelSources(
          List<Path> modelSources,
          Map<String, String> modelPackages,
          String basePackage,
          Set<String> modelClassNames,
          SourcePackageResolver packageResolver) throws IOException {
    var tasks = new ArrayList<Callable<PreprocessResult>>();
    for (var source : modelSources) {
      tasks.add(() -> {
        var fileName = source.getFileName().toString();
        try {
          var className = className(fileName);
          var raw = Files.readString(source);
          var targetPackage = modelPackages.getOrDefault(className, basePackage);
          var rewritten = packageResolver.rewriteModelImports(
                  raw, basePackage, modelPackages, modelClassNames);
          var result = ModelSourcePreprocessor.preprocess(fileName, rewritten, targetPackage);
          return new PreprocessResult(
                  result.className(), fileName, result.preprocessedSource(), targetPackage);
        } catch (IllegalArgumentException e) {
          log.atLevel(Level.DEBUG).log(() -> "[SourceCompiler] %s".formatted(e.getMessage()));
          return null;
        }
      });
    }
    return runPreprocessTasks(tasks, "model");
  }

  private List<PreprocessResult> runPreprocessTasks(
          List<Callable<PreprocessResult>> tasks,
          String label) throws IOException {
    if (tasks.isEmpty()) {
      return List.of();
    }
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<PreprocessResult>>();
      for (var task : tasks) {
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

  private @NonNull List<PreprocessedSource> writePreprocessedSources(
          @NonNull List<PreprocessResult> results,
          @NonNull Path outputDir) throws IOException {
    var written = new ArrayList<PreprocessedSource>();
    for (var result : results) {
      var outputFile = outputFile(outputDir, result.targetPackage(), result.fileName());
      codeWriter.write(outputFile, result.source());
      log.atLevel(Level.DEBUG).log(() -> "[SourceCompiler] Preprocessed %s -> %s"
              .formatted(result.fileName(), outputFile));
      written.add(new PreprocessedSource(result.className(), outputFile, result.targetPackage()));
    }
    return written;
  }

  private static Path outputFile(Path outputDir, String targetPackage, String fileName) {
    if (targetPackage != null && !targetPackage.isBlank()) {
      return outputDir.resolve(targetPackage.replace('.', '/')).resolve(fileName);
    }
    return outputDir.resolve(fileName);
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
        diagnostics.getDiagnostics().forEach(d -> log.atLevel(Level.DEBUG)
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
      log.atLevel(Level.DEBUG).setCause(e)
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
                d -> log.atLevel(Level.DEBUG)
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

  private static @NonNull String qualifiedClassName(
          @NonNull String className,
          String targetPackage) {
    return (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage + "." + className
            : className;
  }

  private static @NonNull String resolveClasspath(CompileOptions options) {
    if (options != null && options.classpath() != null && !options.classpath().isBlank()) {
      return options.classpath();
    }
    var fromProperty = System.getProperty(CompilerConstants.COMPILER_CLASSPATH_PROPERTY);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return fromProperty;
    }
    var defaultClasspath = System.getProperty("java.class.path");
    if (defaultClasspath == null || defaultClasspath.isBlank()) {
      throw new IllegalStateException(
              "[SourceCompiler] No classpath available: provide CompileOptions.classpath(), "
                      + "the '%s' system property, or 'java.class.path'".formatted(
                              CompilerConstants.COMPILER_CLASSPATH_PROPERTY));
    }
    return defaultClasspath;
  }

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  @Deprecated(forRemoval = true)
  private @NonNull List<DslObject> loadDefinitions(
          @NonNull Path outputDir,
          @SuppressWarnings("unused") @NonNull String providerFqcn) {
    URL url;
    try {
      url = outputDir.toUri().toURL();
    } catch (IOException e) {
      log.atLevel(Level.DEBUG).setCause(e).log(
              () -> "[SourceCompiler] Failed to build output URL: %s".formatted(e.getMessage()));
      return List.of();
    }
    var loader = new URLClassLoader(new URL[]{url},
            Thread.currentThread().getContextClassLoader());
    try {
      var providers = ServiceLoader.load(DslDefinitionProvider.class, loader);
      for (var provider : providers) {
        return provider.definitions();
      }
      log.atLevel(Level.DEBUG).log(
              () -> "[SourceCompiler] No DslDefinitionProvider discovered in output directory");
    } catch (Exception e) {
      log.atLevel(Level.DEBUG).setCause(e)
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
          @Deprecated(forRemoval = true)
          Level logLevel,
          String classpath,
          boolean useFileNameSubPackage) {

    public CompileOptions(
            String buildVersion,
            String targetPackage,
            Level logLevel,
            String classpath) {
      this(buildVersion, targetPackage, logLevel, classpath, true);
    }
  }

  private record PreprocessedSource(@NonNull String className, @NonNull Path sourceFile,
          String targetPackage) {
  }

  private record PreprocessResult(@NonNull String className, @NonNull String fileName,
          @NonNull String source, String targetPackage) {
  }

}
