package cbs.nova.dsl.codegen.task;

import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.generator.ModelRegistryGenerator;
import cbs.nova.dsl.codegen.generator.ProcessCodeGenerator;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@RequiredArgsConstructor
public final class GenerateCodeTask implements CompileTask {

  private final ProcessCodeGenerator processCodeGenerator;
  private final TransactionCodeGenerator transactionCodeGenerator;
  private final GeneratedClassProviderGenerator generatedClassProviderGenerator;
  private final ModelRegistryGenerator modelRegistryGenerator;

  @Override
  public String name() {
    return "generate";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var options = context.options();
    final var snapshot = context;
    List<Callable<GenerationChunk>> jobs = new ArrayList<>();
    for (var process : context.processes()) {
      jobs.add(() -> generateProcess(process, snapshot));
    }
    for (var transaction : context.transactions()) {
      jobs.add(() -> generateTransaction(transaction, snapshot));
    }
    var chunks = VirtualThreads.runAll(jobs);
    var modelRegistrySource = modelRegistryGenerator.generate(
            options.srcDir(), options.outputDir(), options.targetPackage(), options.buildVersion(),
            options.useFileNameSubPackage());

    var sources = new ArrayList<GeneratedSource>();
    var providerFqns = new ArrayList<String>();
    for (var chunk : chunks) {
      sources.addAll(chunk.sources());
      if (chunk.providerFqn() != null) {
        providerFqns.add(chunk.providerFqn());
      }
    }
    sources.add(modelRegistrySource);

    return context.toBuilder()
            .generatedSources(sources)
            .providerFqns(providerFqns)
            .build();
  }

  private GenerationChunk generateProcess(ProcessDescriptor process, CompileContext context) {
    var options = context.options();
    var sources = new ArrayList<>(processCodeGenerator.generate(
            process, options.buildVersion(), options.targetPackage(),
            options.useFileNameSubPackage()));
    var provider = generatedClassProviderGenerator.forProcess(
            process, context.preprocessedSources(), options.buildVersion(), options.targetPackage(),
            options.useFileNameSubPackage());
    sources.add(provider);
    return new GenerationChunk(sources, provider.fullyQualifiedName());
  }

  private GenerationChunk generateTransaction(TransactionDescriptor transaction,
          CompileContext context) {
    var options = context.options();
    var sources = new ArrayList<>(transactionCodeGenerator.generate(
            transaction, options.buildVersion(), options.targetPackage(),
            options.useFileNameSubPackage()));
    var provider = generatedClassProviderGenerator.forTransaction(
            transaction, context.preprocessedSources(), options.buildVersion(),
            options.targetPackage(), options.useFileNameSubPackage());
    sources.add(provider);
    return new GenerationChunk(sources, provider.fullyQualifiedName());
  }

  private record GenerationChunk(List<GeneratedSource> sources, String providerFqn) {
  }
}
