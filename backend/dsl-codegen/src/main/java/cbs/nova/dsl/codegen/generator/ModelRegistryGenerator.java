package cbs.nova.dsl.codegen.generator;

import cbs.nova.dsl.ModelRegistry;
import cbs.nova.dsl.codegen.CodeWriter;
import cbs.nova.dsl.codegen.CompilerConstants;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.ModelTypeExtractor;
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

                  import cbs.nova.dsl.ModelRegistry;
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
          String targetPackage) throws IOException {
    var modelDir = srcDir.resolve(CompilerConstants.MODELS_FOLDER);
    List<String> typeNames = collectTypeNames(modelDir);

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

  private List<String> collectTypeNames(Path modelDir) throws IOException {
    if (!Files.isDirectory(modelDir)) {
      return List.of();
    }
    var names = new ArrayList<String>();
    try (Stream<Path> stream = Files.walk(modelDir)) {
      for (Path file : stream.toList()) {
        if (!file.toString().endsWith(".java")) {
          continue;
        }
        var fileName = file.getFileName().toString();
        var rawSource = Files.readString(file);
        names.addAll(modelTypeExtractor.extract(fileName, rawSource));
      }
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
