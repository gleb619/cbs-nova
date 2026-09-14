package cbs.nova.dsl.codegen.task;

import static cbs.nova.dsl.config.Constants.JAVA_FILE_SUFFIX;

import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.codegen.generator.ModelRegistryGenerator;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.CodeWriter;
import cbs.nova.dsl.registry.ModelRegistry;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.concurrent.Callable;

@RequiredArgsConstructor
public final class WriteOutputTask implements CompileTask {

  private final CodeWriter codeWriter;

  @Override
  public String name() {
    return "write";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var options = context.options();
    VirtualThreads.runAll(context.generatedSources().stream()
            .<Callable<Void>>map(source -> () -> {
              write(source, options.outputDir());
              return null;
            })
            .toList());
    codeWriter.writeServiceFile(GeneratedClassProvider.class.getName(), context.providerFqns(),
            options.outputDir());
    codeWriter.writeServiceFile(ModelRegistry.class.getName(),
            context.generatedSources().stream()
                    .filter(source -> source.className().equals(
                            ModelRegistryGenerator.REGISTRY_CLASS))
                    .map(GeneratedSource::fullyQualifiedName)
                    .toList(),
            options.outputDir());

    return context;
  }

  private void write(GeneratedSource source, Path outputDir) throws IOException {
    var packagePath = source.packageName().replace('.', '/');
    var dir = outputDir.resolve(packagePath);
    codeWriter.createDirectories(dir);
    codeWriter.write(dir.resolve(source.className() + JAVA_FILE_SUFFIX), source.source());
  }
}
