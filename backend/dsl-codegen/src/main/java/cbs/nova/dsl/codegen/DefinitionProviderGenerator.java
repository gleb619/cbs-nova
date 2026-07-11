package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.utils.Substitutor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public final class DefinitionProviderGenerator {

  static final String PROVIDER_CLASS = "GeneratedDslDefinitionProvider";
  static final String PROVIDER_FQCN = PROVIDER_CLASS;
  static final String SERVICE_PATH = "META-INF/services/" + DslDefinitionProvider.class.getName();

  private static final String SOURCE_TEMPLATE = """
          import cbs.nova.dsl.DslDefinitionProvider;
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
    Files.createDirectories(outputDir);
    var sourceFile = outputDir.resolve(PROVIDER_CLASS + ".java");
    var registrations = classNames.stream()
            .map(name -> "    result.addAll(new " + name + "().define());")
            .collect(Collectors.joining("\n"));
    if (!registrations.isEmpty()) {
      registrations = registrations + "\n";
    }
    var source = Substitutor.format(SOURCE_TEMPLATE, Map.of(
            "className", PROVIDER_CLASS,
            "registrations", registrations));
    Files.writeString(sourceFile, source);
    log.info("[DefinitionProviderGenerator] Wrote provider source to {}", sourceFile);

    var serviceFile = outputDir.resolve(SERVICE_PATH);
    Files.createDirectories(serviceFile.getParent());
    Files.writeString(serviceFile, PROVIDER_FQCN + System.lineSeparator());
    log.info("[DefinitionProviderGenerator] Wrote SPI descriptor to {}", serviceFile);

    return PROVIDER_FQCN;
  }
}
