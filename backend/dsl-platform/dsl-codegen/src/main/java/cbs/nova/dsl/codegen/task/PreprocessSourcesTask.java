package cbs.nova.dsl.codegen.task;

import static cbs.nova.dsl.config.Constants.JAVA_FILE_SUFFIX;

import cbs.nova.dsl.codegen.CompilerConstants;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.preprocessor.DslPreprocessor;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

@Slf4j
@RequiredArgsConstructor
public final class PreprocessSourcesTask implements CompileTask {

  private final DslPreprocessor dslPreprocessor;
  private final CodegenNaming codegenNaming;

  @Override
  public String name() {
    return "preprocess";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var options = context.options();
    var dslDir = options.srcDir().resolve(CompilerConstants.DSL_FOLDER);
    if (!Files.isDirectory(dslDir)) {
      return context.toBuilder().preprocessedSources(List.of()).build();
    }
    final var snapshot = context;
    var resolver = new DslPackageNameResolver(codegenNaming);
    List<Path> files;
    try (var stream = Files.walk(dslDir)) {
      files = stream.filter(file -> file.toString().endsWith(JAVA_FILE_SUFFIX)).toList();
    }
    var results = VirtualThreads.runAll(files.stream()
            .<Callable<@Nullable String>>map(file -> () -> preprocess(file, resolver, snapshot))
            .toList());
    return context.toBuilder()
            .preprocessedSources(results.stream().filter(Objects::nonNull).toList())
            .build();
  }

  private @Nullable String preprocess(Path file, DslPackageNameResolver resolver,
          CompileContext context) throws IOException {
    var options = context.options();
    try {
      var fileName = file.getFileName().toString();
      var rawSource = Files.readString(file);
      var packageName = resolver.resolve(
              options.targetPackage(),
              options.buildVersion(),
              fileName,
              options.useFileNameSubPackage());
      var preprocess = dslPreprocessor.preprocess(fileName, rawSource, packageName);
      return preprocess.preprocessedSource();
    } catch (IllegalArgumentException e) {
      log.atLevel(Level.WARN).log(
              () -> "[DslCompiler] Skipping invalid DSL source %s: %s".formatted(file,
                      e.getMessage()));
      return null;
    }
  }
}
