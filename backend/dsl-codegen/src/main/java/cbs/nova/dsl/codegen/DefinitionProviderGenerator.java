package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public final class DefinitionProviderGenerator {

  private final Level logLevel;

  static final String PROVIDER_CLASS = "GeneratedDslDefinitionProvider";
  static final String SERVICE_PATH = "META-INF/services/" + DslDefinitionProvider.class.getName();

  private static final String SOURCE_TEMPLATE = """
          ${packageLine}import cbs.nova.dsl.DslDefinitionProvider;
          import cbs.nova.dsl.DslObject;
          import java.util.ArrayList;
          import java.util.List;

          public class ${className} implements DslDefinitionProvider {
            @Override
            public List<DslObject> definitions() {
              var result = new ArrayList<DslObject>();
          ${registrations}
              return result;
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
    Files.createDirectories(outputDir);
    var packageLine = (targetPackage != null && !targetPackage.isBlank())
            ? "package " + targetPackage + ";\n\n"
            : "";
    var providerFqcn = providerFqcn(targetPackage);
    var sourceFile = sourcePath(outputDir, targetPackage);
    Files.createDirectories(sourceFile.getParent());

    var registrations = classNames.stream()
            .map(name -> "    result.addAll(new " + name + "().define());")
            .collect(Collectors.joining("\n"));
    if (!registrations.isEmpty()) {
      registrations = registrations + "\n";
    }
    var source = Substitutor.format(SOURCE_TEMPLATE, Map.of(
            "packageLine", packageLine,
            "className", PROVIDER_CLASS,
            "registrations", registrations));
    Files.writeString(sourceFile, source);
    log.atLevel(logLevel).log(() -> "[DefinitionProviderGenerator] Wrote provider source to %s"
            .formatted(sourceFile));

    var serviceFile = outputDir.resolve(SERVICE_PATH);
    Files.createDirectories(serviceFile.getParent());
    Files.writeString(serviceFile, providerFqcn + System.lineSeparator());
    log.atLevel(logLevel).log(() -> "[DefinitionProviderGenerator] Wrote SPI descriptor to %s"
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
}
