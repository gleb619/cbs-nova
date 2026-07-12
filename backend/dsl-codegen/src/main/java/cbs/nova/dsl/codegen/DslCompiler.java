package cbs.nova.dsl.codegen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public final class DslCompiler {

  private static final String DEFAULT_LOG_LEVEL = "TRACE";
  private static final String SIMPLE_LOGGER_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      log.error("Usage: DslCompiler <srcDir> <outputDir> [<version>] [<dslPackage>] [<logLevel>]");
      System.exit(1);
    }
    Path srcDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);
    String version = args.length > 2 ? args[2] : null;
    String dslPackage = args.length > 3 ? args[3] : null;
    String logLevel = args.length > 4 ? args[4] : DEFAULT_LOG_LEVEL;
    configureLogLevel(logLevel);
    compile(srcDir, outputDir, version, dslPackage);
  }

  public static void compile(Path srcDir, Path outputDir) throws IOException {
    compile(srcDir, outputDir, "demo", "cbs.nova");
  }

  public static void compile(
          Path srcDir,
          Path outputDir,
          String version) throws IOException {
    compile(srcDir, outputDir, version, "cbs.nova");
  }

  public static void compile(
          Path srcDir,
          Path outputDir,
          String version,
          String dslPackage) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }

    var options = new SourceCompiler.CompileOptions(version, dslPackage);
    var descriptors = new SourceCompiler().compileAndDescribe(srcDir, outputDir, compiler, options);

    var sources = generateSourcesInVirtualThreads(descriptors, version);

    CodeWriter.write(sources, outputDir);
    log.info("[DslCompiler] Generated {} source(s) to {}", sources.size(), outputDir);
  }

  private static @NonNull List<GeneratedSource> generateSourcesInVirtualThreads(
          SourceCompiler.Descriptors descriptors,
          String version) {
    var processGen = new ProcessCodeGenerator();
    var txGen = new TransactionCodeGenerator();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<List<GeneratedSource>> processFuture = executor.submit(() -> {
        var list = new ArrayList<GeneratedSource>();
        for (var p : descriptors.processes()) {
          list.addAll(processGen.generate(p, version));
        }
        return list;
      });
      Future<List<GeneratedSource>> txFuture = executor.submit(() -> {
        var list = new ArrayList<GeneratedSource>();
        for (var t : descriptors.transactions()) {
          list.addAll(txGen.generate(t, version));
        }
        return list;
      });

      var sources = new ArrayList<GeneratedSource>();
      try {
        sources.addAll(processFuture.get());
        sources.addAll(txFuture.get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("[DslCompiler] Source generation interrupted", e);
      } catch (ExecutionException e) {
        var cause = e.getCause();
        if (cause instanceof RuntimeException re) {
          throw re;
        }
        throw new IllegalStateException("[DslCompiler] Source generation failed", cause);
      }
      return sources;
    }
  }

  private static void configureLogLevel(String logLevel) {
    if (logLevel == null || logLevel.isBlank()) {
      logLevel = DEFAULT_LOG_LEVEL;
    }
    System.setProperty(SIMPLE_LOGGER_LEVEL_PROPERTY, logLevel.toLowerCase());
  }
}
