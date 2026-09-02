package cbs.nova.dsl.codegen.generator;

import cbs.nova.dsl.codegen.CodeWriter;
import cbs.nova.dsl.codegen.CompilerConstants;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.ModelTypeExtractor;
import cbs.nova.dsl.codegen.util.SourcePackageResolver;
import cbs.nova.dsl.registry.ModelRegistry;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public final class ModelRegistryGenerator {

  public static final String REGISTRY_CLASS = "GeneratedModelRegistry";
  public static final String SERVICE_PATH = "META-INF/services/"
          + ModelRegistry.class.getName();

  private final CodeWriter codeWriter;
  private final CodegenNaming codegenNaming;
  private final ModelTypeExtractor modelTypeExtractor;

  private static final String SOURCE_TEMPLATE = // language=java
          """
                  package ${pkg};

                  import cbs.nova.dsl.registry.ModelRegistry;
                  import cbs.nova.dsl.annotation.DslGenerated;
                  import java.util.Set;
                  import org.jspecify.annotations.NonNull;
                  import javax.annotation.processing.Generated;

                  ${annotation}
                  public final class ${className} implements ModelRegistry {

                    private static final Set<Class<?>> MODEL_TYPES = Set.of(
                  ${entries}
                    );

                    @Override
                    public @NonNull Set<Class<?>> modelTypes() {
                      return MODEL_TYPES;
                    }
                  }
                  """;

  public @NonNull GeneratedSource generate(
          @NonNull Path srcDir,
          @NonNull Path outputDir,
          String targetPackage,
          boolean useFileNameSubPackage) throws IOException {
    var dslDir = srcDir.resolve(CompilerConstants.DSL_FOLDER);
    var modelDir = srcDir.resolve(CompilerConstants.MODELS_FOLDER);
    var dslSources = collectJavaSources(dslDir);
    var modelSources = collectJavaSources(modelDir);

    var packageResolver = new SourcePackageResolver(codegenNaming);
    var dslPackages = packageResolver.resolveDslPackages(
            dslSources, targetPackage, null, useFileNameSubPackage);
    var modelPackages = packageResolver.resolveModelPackages(
            dslSources, modelSources, targetPackage, dslPackages);

    List<String> typeNames = collectTypeNames(modelSources, modelPackages);

    String pkg = codegenNaming.registryPackage(targetPackage);
    String fqcn = fqcn(pkg, REGISTRY_CLASS);
    String entries = buildEntries(typeNames);

    String annotation = GeneratorMetadata.annotation(ModelRegistryGenerator.class);
    String source = Substitutor.format(SOURCE_TEMPLATE, Map.of(
            "pkg", pkg,
            "annotation", annotation,
            "className", REGISTRY_CLASS,
            "entries", entries));

    var sourceFile = outputDir.resolve(pkg.replace('.', '/')).resolve(REGISTRY_CLASS + ".java");
    codeWriter.write(sourceFile, source);

    var serviceFile = outputDir.resolve(SERVICE_PATH);
    codeWriter.write(serviceFile, fqcn + System.lineSeparator());

    log.atLevel(Level.DEBUG)
            .log(() -> "[ModelRegistryGenerator] Wrote model registry %s with %d type(s)"
                    .formatted(sourceFile, typeNames.size()));
    return new GeneratedSource(pkg, REGISTRY_CLASS, source);
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

  private List<String> collectTypeNames(
          List<Path> modelSources,
          Map<String, String> modelPackages) throws IOException {
    var names = new ArrayList<String>();
    for (Path file : modelSources) {
      var fileName = file.getFileName().toString();
      var className = fileName.substring(0, fileName.length() - ".java".length());
      var rawSource = Files.readString(file);
      var packageName = modelPackages.getOrDefault(className, null);
      names.addAll(modelTypeExtractor.extract(fileName, rawSource, packageName));
    }
    return names;
  }

  private String buildEntries(List<String> typeNames) {
    return typeNames.stream()
            .map(n -> "    " + n + ".class")
            .collect(Collectors.joining(",\n"));
  }

  private String fqcn(String pkg, String className) {
    return pkg.isEmpty() ? className : pkg + "." + className;
  }
}
