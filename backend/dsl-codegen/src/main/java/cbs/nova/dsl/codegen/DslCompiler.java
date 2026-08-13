package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.codegen.SemanticValidator;
import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.generator.ProcessCodeGenerator;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class DslCompiler {

  private final DslSourceCompiler dslSourceCompiler;
  private final ProcessCodeGenerator processCodeGenerator;
  private final TransactionCodeGenerator transactionCodeGenerator;
  private final GeneratedClassProviderGenerator generatedClassProviderGenerator;
  private final CodeWriter codeWriter;
  private final DescriptorFactory descriptorFactory;
  private final SemanticValidator semanticValidator;
  private final HelperRegistry helperRegistry;
  private final Level logLevel;

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      log.atLevel(Level.ERROR).log(
              () -> "Usage: DslCompiler <srcDir> <outputDir> [version] [targetPackage] [logLevel]");
      System.exit(1);
    }
    var srcDir = Path.of(args[0]);
    var outputDir = Path.of(args[1]);
    var version = args.length > 2 ? nullIfBlank(args[2]) : null;
    var targetPackage = args.length > 3 ? nullIfBlank(args[3]) : null;
    var logLevelArg = args.length > 4 ? nullIfBlank(args[4]) : null;
    var logLevel = logLevelArg != null ? Level.valueOf(logLevelArg.toUpperCase()) : Level.INFO;
    compile(srcDir, outputDir, version, targetPackage, logLevel);
  }

  public static void compile(Path srcDir, Path outputDir) throws IOException {
    compile(srcDir, outputDir, null, null, Level.INFO);
  }

  public static void compile(
          Path srcDir, Path outputDir, String version, String targetPackage) throws IOException {
    compile(srcDir, outputDir, version, targetPackage, Level.INFO);
  }

  public static void compile(
          Path srcDir,
          Path outputDir,
          String version,
          String targetPackage,
          Level logLevel) throws IOException {
    compile(srcDir, outputDir, version, targetPackage, logLevel, null);
  }

  public static void compile(
          Path srcDir,
          Path outputDir,
          String version,
          String targetPackage,
          Level logLevel,
          String classpath) throws IOException {
    CompileConfig.compileConfig(logLevel)
            .dslCompiler()
            .compileInternal(srcDir, outputDir, version, targetPackage, classpath);
  }

  private void compileInternal(
          @NonNull Path srcDir,
          @NonNull Path outputDir,
          String version,
          String targetPackage,
          String classpath) throws IOException {
    var options = new SourceCompiler.CompileOptions(version, targetPackage, logLevel, classpath);
    List<DslObject> objects = dslSourceCompiler.compileAndLoad(srcDir, outputDir, options);
    List<String> preprocessedSources = preprocessedDslSources(srcDir, targetPackage);

    var processes = new ArrayList<ProcessDescriptor>();
    var transactions = new ArrayList<TransactionDescriptor>();
    var functions = new ArrayList<FunctionDescriptor>();

    for (DslObject obj : objects) {
      switch (obj.type()) {
        case PROCESS -> processes.add(descriptorFactory.fromProcess((ProcessDslObject) obj));
        case TRANSACTION ->
          transactions.add(descriptorFactory.fromTransaction((TransactionDslObject) obj));
        case FUNCTION ->
          functions.add(descriptorFactory.fromFunction((FunctionDslObject) obj));
      }
    }

    semanticValidator.validate(processes, transactions, functions,
            helperRegistry);

    var sources = new ArrayList<GeneratedSource>();
    var providerFqns = new ArrayList<String>();

    for (var p : processes) {
      sources.addAll(processCodeGenerator.generate(p, version, targetPackage));
      var provider = generatedClassProviderGenerator.forProcess(
              p, preprocessedSources, version, targetPackage);
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }
    for (var t : transactions) {
      sources.addAll(transactionCodeGenerator.generate(t, version, targetPackage));
      var provider = generatedClassProviderGenerator.forTransaction(
              t, preprocessedSources, version, targetPackage);
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }

    codeWriter.write(sources, outputDir);
    codeWriter.writeServiceFile(GeneratedClassProvider.class.getName(), providerFqns, outputDir);
    log.atLevel(logLevel).log(() -> "[DslCompiler] Generated %s source(s) to %s"
            .formatted(sources.size(), outputDir));
  }

  private static @NonNull List<String> preprocessedDslSources(
          @NonNull Path srcDir,
          String targetPackage) throws IOException {
    var dslDir = srcDir.resolve(CompilerConstants.DSL_FOLDER);
    if (!Files.isDirectory(dslDir)) {
      return List.of();
    }
    var result = new ArrayList<String>();
    try (var stream = Files.walk(dslDir)) {
      for (Path file : stream.toList()) {
        if (!file.toString().endsWith(".java")) {
          continue;
        }
        try {
          var fileName = file.getFileName().toString();
          var rawSource = Files.readString(file);
          var preprocess = CompactSourcePreprocessor.preprocess(fileName, rawSource, targetPackage);
          result.add(preprocess.preprocessedSource());
        } catch (IllegalArgumentException e) {
          log.atLevel(Level.WARN).log(
                  () -> "[DslCompiler] Skipping invalid DSL source %s: %s".formatted(file,
                          e.getMessage()));
        }
      }
    }
    return result;
  }

  private static String nullIfBlank(String value) {
    return value != null && !value.isBlank() ? value : null;
  }
}
