package cbs.nova.dsl.codegen;

import static cbs.nova.dsl.codegen.CompilerConstants.DEFAULT_BUILD_VERSION;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.generator.ModelRegistryGenerator;
import cbs.nova.dsl.codegen.generator.ProcessCodeGenerator;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.DslCompilerOptions;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.registry.ModelRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class DslCompiler {

  private final ModelRegistryGenerator modelRegistryGenerator;
  private final DslSourceCompiler dslSourceCompiler;
  private final ProcessCodeGenerator processCodeGenerator;
  private final TransactionCodeGenerator transactionCodeGenerator;
  private final GeneratedClassProviderGenerator generatedClassProviderGenerator;
  private final CodeWriter codeWriter;
  private final DescriptorFactory descriptorFactory;
  private final SemanticValidator semanticValidator;
  private final HelperRegistry helperRegistry;
  private final CodegenNaming codegenNaming;


  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      log.atLevel(Level.ERROR).log(
              () -> "Usage: DslCompiler <serialized java.util.Properties>");
      System.exit(1);
    }
    var options = DslCompilerOptions.fromProperties(args[0]);
    compile(options);
  }

  public static void compile(@NonNull DslCompilerOptions options) throws IOException {
    CompileConfig.compileConfig()
            .dslCompiler()
            .compileInternal(options);
  }

  private void compileInternal(@NonNull DslCompilerOptions options) throws IOException {
    var timings = new ArrayList<StepTiming>();
    var sourceOptions = new SourceCompiler.CompileOptions(
            options.buildVersion(),
            options.targetPackage(),
            options.logLevel(),
            options.classpath(),
            options.useFileNameSubPackage());

    var loadStart = Instant.now();
    List<DslObject> objects = dslSourceCompiler.compileAndLoad(
            options.srcDir(), options.outputDir(), sourceOptions);
    timings.add(timing("load", loadStart));

    var preprocessStart = Instant.now();
    List<String> preprocessedSources = preprocessedDslSources(options);
    timings.add(timing("preprocess", preprocessStart));

    var describeStart = Instant.now();
    var processes = new ArrayList<ProcessDescriptor>();
    var transactions = new ArrayList<TransactionDescriptor>();
    var functions = new ArrayList<FunctionDescriptor>();

    for (DslObject obj : objects) {
      switch (obj.type()) {
        case PROCESS -> processes.add(descriptorFactory.fromProcess((ProcessDslObject) obj));
        case TRANSACTION ->
          transactions.add(descriptorFactory.fromTransaction((TransactionDslObject) obj));
        case FUNCTION -> functions.add(descriptorFactory.fromFunction((FunctionDslObject) obj));
      }
    }
    timings.add(timing("describe", describeStart));

    var validationStart = Instant.now();
    semanticValidator.validate(processes, transactions, functions, helperRegistry);
    timings.add(timing("validate", validationStart));

    var generationStart = Instant.now();
    var sources = new ArrayList<GeneratedSource>();
    var providerFqns = new ArrayList<String>();

    for (var p : processes) {
      sources.addAll(processCodeGenerator.generate(
              p, options.buildVersion(), options.targetPackage(),
              options.useFileNameSubPackage()));
      var provider = generatedClassProviderGenerator.forProcess(
              p, preprocessedSources, options.buildVersion(), options.targetPackage(),
              options.useFileNameSubPackage());
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }
    for (var t : transactions) {
      sources.addAll(transactionCodeGenerator.generate(
              t, options.buildVersion(), options.targetPackage(),
              options.useFileNameSubPackage()));
      var provider = generatedClassProviderGenerator.forTransaction(
              t, preprocessedSources, options.buildVersion(), options.targetPackage(),
              options.useFileNameSubPackage());
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }

    var modelRegistrySource = modelRegistryGenerator.generate(
            options.srcDir(), options.outputDir(), options.targetPackage(),
            options.useFileNameSubPackage());
    sources.add(modelRegistrySource);
    timings.add(timing("generate", generationStart));

    var writeStart = Instant.now();
    codeWriter.write(sources, options.outputDir());
    codeWriter.writeServiceFile(GeneratedClassProvider.class.getName(), providerFqns,
            options.outputDir());
    codeWriter.writeServiceFile(ModelRegistry.class.getName(),
            List.of(modelRegistrySource.fullyQualifiedName()), options.outputDir());
    timings.add(timing("write", writeStart));

    logSummary(timings, sources.size(), options.outputDir());
  }

  private static StepTiming timing(String phase, Instant start) {
    return new StepTiming(phase, Duration.between(start, Instant.now()));
  }

  private static void logSummary(List<StepTiming> timings, int sourceCount, Path outputDir) {
    var total = timings.stream()
            .map(StepTiming::duration)
            .reduce(Duration.ZERO, Duration::plus);
    log.atLevel(Level.INFO).log(() -> "[DslCompiler] Generated %s source(s) to %s in %s"
            .formatted(sourceCount, outputDir, humanReadable(total)));
    String report = timings.stream()
            .map(t -> "  %s - %s".formatted(t.phase(), humanReadable(t.duration())))
            .collect(Collectors.joining("\n"));
    log.atLevel(Level.DEBUG).log(() -> "[DslCompiler] Generation report: \n%s"
            .formatted(report));
  }

  private static String humanReadable(Duration duration) {
    var millis = duration.toMillis();
    return millis < 1000 ? millis + " ms" : "%.2f s".formatted(millis / 1000.0);
  }

  private @NonNull List<String> preprocessedDslSources(@NonNull DslCompilerOptions options)
          throws IOException {
    var dslDir = options.srcDir().resolve(CompilerConstants.DSL_FOLDER);
    if (!Files.isDirectory(dslDir)) {
      return List.of();
    }
    var resolver = new DslPackageNameResolver(codegenNaming);
    var result = new ArrayList<String>();
    try (var stream = Files.walk(dslDir)) {
      for (Path file : stream.toList()) {
        if (!file.toString().endsWith(".java")) {
          continue;
        }
        try {
          var fileName = file.getFileName().toString();
          var rawSource = Files.readString(file);
          var packageName = resolver.resolve(
                  options.targetPackage(),
                  options.buildVersion(),
                  fileName,
                  options.useFileNameSubPackage());
          var preprocess = CompactSourcePreprocessor.preprocess(fileName, rawSource, packageName);
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

  private record StepTiming(String phase, Duration duration) {
  }
}
