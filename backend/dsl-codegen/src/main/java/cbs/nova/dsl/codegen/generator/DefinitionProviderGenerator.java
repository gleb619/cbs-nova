package cbs.nova.dsl.codegen.generator;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.codegen.CodeWriter;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public final class DefinitionProviderGenerator {

  private final CodeWriter codeWriter;

  public static final String PROVIDER_CLASS = "GeneratedDslDefinitionProvider";
  public static final String SERVICE_PATH = "META-INF/services/"
          + DslDefinitionProvider.class.getName();

  private static final String SOURCE_TEMPLATE = // language=java
          """
                  ${packageLine}import cbs.nova.dsl.DslCompactSource;
                  import cbs.nova.dsl.DslDefinitionProvider;
                  import cbs.nova.dsl.DslObject;
                  import cbs.nova.dsl.annotation.DslGenerated;
                  import javax.annotation.processing.Generated;
                  import java.util.ArrayList;
                  import java.util.List;

                  ${annotation}
                  public class ${className} implements DslDefinitionProvider {
                    @Override
                    public List<DslObject> definitions() {
                      var sources = new ArrayList<DslCompactSource>();
                  ${registrations}
                      return sources.stream()
                              .map(DslCompactSource::define)
                              .flatMap(List::stream)
                              .toList();
                    }
                  }
                  """;

  public @NonNull String generate(@NonNull Path outputDir, @NonNull List<String> classNames)
          throws IOException {
    return generate(outputDir, classNames, null);
  }

  public @NonNull String generate(
          @NonNull Path outputDir,
          @NonNull List<String> classNames,
          String targetPackage) throws IOException {
    codeWriter.createDirectories(outputDir);
    var packageLine = (targetPackage != null && !targetPackage.isBlank())
            ? "package %s;\n\n".formatted(targetPackage)
            : "";
    var providerFqcn = providerFqcn(targetPackage);
    var sourceFile = sourcePath(outputDir, targetPackage);

    var registrations = createRegistrations(classNames);
    var annotation = GeneratorMetadata.annotation(DefinitionProviderGenerator.class);
    var source = Substitutor.format(SOURCE_TEMPLATE, Map.of(
            "packageLine", packageLine,
            "className", PROVIDER_CLASS,
            "registrations", registrations,
            "annotation", annotation));
    codeWriter.write(sourceFile, source);
    log.atLevel(Level.DEBUG).log(() -> "[DefinitionProviderGenerator] Wrote provider source to %s"
            .formatted(sourceFile));

    var serviceFile = outputDir.resolve(SERVICE_PATH);
    codeWriter.write(serviceFile, providerFqcn + System.lineSeparator());
    log.atLevel(Level.DEBUG).log(() -> "[DefinitionProviderGenerator] Wrote SPI descriptor to %s"
            .formatted(serviceFile));

    return providerFqcn;
  }

  static @NonNull String providerFqcn(String targetPackage) {
    if (targetPackage == null || targetPackage.isBlank()) {
      return PROVIDER_CLASS;
    }
    return targetPackage + "." + PROVIDER_CLASS;
  }

  private static @NonNull Path sourcePath(@NonNull Path outputDir, String targetPackage) {
    if (targetPackage == null || targetPackage.isBlank()) {
      return outputDir.resolve(PROVIDER_CLASS + ".java");
    }
    return outputDir.resolve(targetPackage.replace('.', '/')).resolve(PROVIDER_CLASS + ".java");
  }

  private String createRegistrations(List<String> classNames) {
    var registrations = classNames.stream()
            .map("    sources.add(new %s());"::formatted)
            .collect(Collectors.joining("\n"));

    if (!registrations.isEmpty()) {
      return registrations + "\n";
    }
    return registrations;
  }
}
