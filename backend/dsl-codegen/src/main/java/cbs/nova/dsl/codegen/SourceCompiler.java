package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DescriptorFactory;
import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.SemanticValidator;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

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
import java.util.ServiceLoader;
import java.util.stream.Stream;

@Slf4j
public final class SourceCompiler {

  public record Descriptors(
          @NonNull List<ProcessDescriptor> processes,
          @NonNull List<TransactionDescriptor> transactions,
          @NonNull List<FunctionDescriptor> functions) {
  }

  private record PreprocessedSource(@NonNull String className, @NonNull Path sourceFile) {
  }

  public @NonNull Descriptors compileAndDescribe(
          @NonNull Path srcDir, @NonNull Path outputDir, @NonNull JavaCompiler compiler)
          throws IOException {
    var objects = compileAndLoad(srcDir, outputDir, compiler);
    var processes = new ArrayList<ProcessDescriptor>();
    var transactions = new ArrayList<TransactionDescriptor>();
    var functions = new ArrayList<FunctionDescriptor>();
    for (var obj : objects) {
      switch (obj.type()) {
        case PROCESS -> processes.add(new DescriptorFactory().fromProcess((ProcessDslObject) obj));
        case TRANSACTION ->
          transactions.add(new DescriptorFactory().fromTransaction((TransactionDslObject) obj));
        case FUNCTION ->
          functions.add(new DescriptorFactory().fromFunction((FunctionDslObject) obj));
      }
    }
    new SemanticValidator().validate(processes, transactions, functions,
            new DefaultHelperRegistry());
    return new Descriptors(processes, transactions, functions);
  }

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path srcDir, @NonNull Path outputDir, @NonNull JavaCompiler compiler)
          throws IOException {
    Files.createDirectories(outputDir);
    var classpath = System.getProperty("java.class.path");

    List<Path> sourceFiles;
    try (Stream<Path> stream = Files.walk(srcDir)) {
      sourceFiles = stream
              .filter(p -> p.toString().endsWith(".java"))
              .toList();
    }
    if (sourceFiles.isEmpty()) {
      log.warn("[SourceCompiler] No .java sources found under {}", srcDir);
      return List.of();
    }

    var preprocessed = new ArrayList<PreprocessedSource>();
    for (var file : sourceFiles) {
      var preprocessedSource = preprocess(file, outputDir);
      if (preprocessedSource != null) {
        preprocessed.add(preprocessedSource);
      }
    }
    if (preprocessed.isEmpty()) {
      log.warn("[SourceCompiler] No valid compact DSL sources found under {}", srcDir);
      return List.of();
    }

    var compiledClassNames = new ArrayList<String>();
    for (var source : preprocessed) {
      if (compileSingleSource(compiler, classpath, source.sourceFile(), outputDir)) {
        compiledClassNames.add(source.className());
      }
    }
    if (compiledClassNames.isEmpty()) {
      log.error("[SourceCompiler] No DSL sources compiled successfully");
      return List.of();
    }

    new DefinitionProviderGenerator().generate(outputDir, compiledClassNames);
    var providerSource = outputDir.resolve(DefinitionProviderGenerator.PROVIDER_CLASS + ".java");
    compileProvider(compiler, classpath, providerSource, outputDir);

    return loadDefinitions(outputDir);
  }

  private PreprocessedSource preprocess(@NonNull Path sourceFile, @NonNull Path outputDir)
          throws IOException {
    var fileName = sourceFile.getFileName().toString();
    var rawSource = Files.readString(sourceFile);
    try {
      var result = CompactSourcePreprocessor.preprocess(fileName, rawSource);
      var outputFile = outputDir.resolve(fileName);
      Files.writeString(outputFile, result.preprocessedSource());
      log.info("[SourceCompiler] Preprocessed {} -> {}", sourceFile, outputFile);
      return new PreprocessedSource(result.className(), outputFile);
    } catch (IllegalArgumentException e) {
      log.error("[SourceCompiler] {}", e.getMessage());
      return null;
    }
  }

  private boolean compileSingleSource(
          @NonNull JavaCompiler compiler,
          @NonNull String classpath,
          @NonNull Path sourceFile,
          @NonNull Path outputDir) {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      var unit = fm.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
      var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
      if (!task.call()) {
        diagnostics.getDiagnostics().forEach(d -> log.error(
                "[SourceCompiler] {}: {}", sourceFile.getFileName(), d.getMessage(null)));
        return false;
      }
      return true;
    } catch (IOException e) {
      log.error("[SourceCompiler] Compilation of {} failed: {}",
              sourceFile.getFileName(), e.getMessage(), e);
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
                d -> log.error("[SourceCompiler] provider: {}", d.getMessage(null)));
        throw new IllegalStateException("Failed to compile generated DSL definition provider");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to compile generated DSL definition provider", e);
    }
  }

  private @NonNull List<DslObject> loadDefinitions(@NonNull Path outputDir) {
    URL url;
    try {
      url = outputDir.toUri().toURL();
    } catch (IOException e) {
      log.error("[SourceCompiler] Failed to build output URL: {}", e.getMessage(), e);
      return List.of();
    }
    try (var loader = new URLClassLoader(new URL[]{url},
            Thread.currentThread().getContextClassLoader())) {
      var providers = ServiceLoader.load(DslDefinitionProvider.class, loader);
      for (var provider : providers) {
        return provider.definitions();
      }
      log.error("[SourceCompiler] No DslDefinitionProvider found in {}", outputDir);
    } catch (Exception e) {
      log.error("[SourceCompiler] Failed to load DSL definitions: {}", e.getMessage(), e);
    }
    return List.of();
  }
}
