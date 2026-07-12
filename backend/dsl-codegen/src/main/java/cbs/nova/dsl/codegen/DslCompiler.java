package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.SemanticValidator;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class DslCompiler {

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      log.error("Usage: DslCompiler <srcDir> <outputDir> [version] [targetPackage] [logLevel]");
      System.exit(1);
    }
    var srcDir = Path.of(args[0]);
    var outputDir = Path.of(args[1]);
    var version = args.length > 2 ? nullIfBlank(args[2]) : null;
    var targetPackage = args.length > 3 ? nullIfBlank(args[3]) : null;
    compile(srcDir, outputDir, version, targetPackage);
  }

  public static void compile(Path srcDir, Path outputDir) throws IOException {
    compile(srcDir, outputDir, null, null);
  }

  public static void compile(
          Path srcDir, Path outputDir, String version, String targetPackage) throws IOException {
    var options = new SourceCompiler.CompileOptions(version, targetPackage);
    List<DslObject> objects = new DslSourceCompiler().compileAndLoad(srcDir, outputDir, options);

    var processes = new ArrayList<ProcessDescriptor>();
    var transactions = new ArrayList<TransactionDescriptor>();
    var functions = new ArrayList<FunctionDescriptor>();

    for (DslObject obj : objects) {
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

    var processGen = new ProcessCodeGenerator();
    var txGen = new TransactionCodeGenerator();
    var providerGen = new GeneratedClassProviderGenerator();
    var sources = new ArrayList<GeneratedSource>();
    var providerFqns = new ArrayList<String>();

    for (var p : processes) {
      sources.addAll(processGen.generate(p, version));
      var provider = providerGen.forProcess(p);
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }
    for (var t : transactions) {
      sources.addAll(txGen.generate(t, version));
      var provider = providerGen.forTransaction(t);
      sources.add(provider);
      providerFqns.add(provider.fullyQualifiedName());
    }

    CodeWriter.write(sources, outputDir);
    CodeWriter.writeServiceFile(GeneratedClassProvider.class.getName(), providerFqns, outputDir);
    log.info("[DslCompiler] Generated {} source(s) to {}", sources.size(), outputDir);
  }

  private static String nullIfBlank(String value) {
    return value != null && !value.isBlank() ? value : null;
  }
}
