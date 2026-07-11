package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.DescriptorFactory;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.SemanticValidator;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DslCompiler {

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("Usage: DslCompiler <srcDir> <outputDir>");
      System.exit(1);
    }
    compile(Path.of(args[0]), Path.of(args[1]));
  }

  public static void compile(Path srcDir, Path outputDir) throws IOException {
    List<DslObject> objects = DefinitionLoader.loadObjects(srcDir);

    var processes = new ArrayList<ProcessDescriptor>();
    var transactions = new ArrayList<TransactionDescriptor>();
    var functions = new ArrayList<FunctionDescriptor>();

    for (DslObject obj : objects) {
      switch (obj.type()) {
        case PROCESS -> processes.add(DescriptorFactory.fromProcess((ProcessDslObject) obj));
        case TRANSACTION ->
          transactions.add(DescriptorFactory.fromTransaction((TransactionDslObject) obj));
        case FUNCTION -> functions.add(DescriptorFactory.fromFunction((FunctionDslObject) obj));
      }
    }

    SemanticValidator.validate(processes, transactions, functions, new DefaultHelperRegistry());

    var processGen = new ProcessCodeGenerator();
    var txGen = new TransactionCodeGenerator();
    var sources = new ArrayList<GeneratedSource>();
    for (var p : processes)
      sources.addAll(processGen.generate(p));
    for (var t : transactions)
      sources.addAll(txGen.generate(t));

    CodeWriter.write(sources, outputDir);
    System.out.println("[DslCompiler] Generated " + sources.size() + " source(s) to " + outputDir);
  }
}
