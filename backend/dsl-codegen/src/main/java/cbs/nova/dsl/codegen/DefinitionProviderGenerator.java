package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a {@link DslDefinitionProvider} implementation that aggregates the {@code define()}
 * outputs of one or more compiled compact DSL source classes. The generator writes the provider
 * source plus the matching {@code META-INF/services} descriptor so the result can be discovered via
 * {@link java.util.ServiceLoader}.
 *
 * <p>
 * The generated class is placed in the default package because the compact DSL source classes it
 * references are themselves in the default package; the generated code therefore cannot import them
 * and must share their package.
 */
@Slf4j
public final class DefinitionProviderGenerator {

  static final String PROVIDER_CLASS = "GeneratedDslDefinitionProvider";
  static final String PROVIDER_FQCN = PROVIDER_CLASS;
  static final String SERVICE_PATH = "META-INF/services/" + DslDefinitionProvider.class.getName();

  /**
   * Writes the provider source and SPI file into {@code outputDir}.
   *
   * @param outputDir
   *          directory where the generated {@code .java} and SPI descriptor will be written. Must
   *          exist or be creatable. The provider is written directly into this directory because it
   *          lives in the default package.
   * @param classNames
   *          the simple class names (no package) of the compiled DSL classes whose {@code define()}
   *          methods should be invoked.
   * @return the fully-qualified provider class name.
   */
  public @NonNull String generate(@NonNull Path outputDir, @NonNull List<String> classNames)
          throws IOException {
    Files.createDirectories(outputDir);
    var sourceFile = outputDir.resolve(PROVIDER_CLASS + ".java");
    var registrations = classNames.stream()
            .map(name -> "    result.addAll(new " + name + "().define());")
            .collect(Collectors.joining("\n"));
    var source = renderSource(registrations);
    Files.writeString(sourceFile, source);
    log.info("[DefinitionProviderGenerator] Wrote provider source to {}", sourceFile);

    var serviceFile = outputDir.resolve(SERVICE_PATH);
    Files.createDirectories(serviceFile.getParent());
    Files.writeString(serviceFile, PROVIDER_FQCN + System.lineSeparator());
    log.info("[DefinitionProviderGenerator] Wrote SPI descriptor to {}", serviceFile);

    return PROVIDER_FQCN;
  }

  private @NonNull String renderSource(@NonNull String registrations) {
    var sb = new StringBuilder();
    sb.append("import cbs.nova.dsl.DslDefinitionProvider;").append(System.lineSeparator());
    sb.append("import cbs.nova.dsl.DslObject;").append(System.lineSeparator());
    sb.append("import java.util.ArrayList;").append(System.lineSeparator());
    sb.append("import java.util.List;").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    sb.append("public class ").append(PROVIDER_CLASS)
            .append(" implements DslDefinitionProvider {").append(System.lineSeparator());
    sb.append("  @Override").append(System.lineSeparator());
    sb.append("  public List<DslObject> definitions() {").append(System.lineSeparator());
    sb.append("    var result = new ArrayList<DslObject>();").append(System.lineSeparator());
    if (!registrations.isEmpty()) {
      sb.append(registrations).append(System.lineSeparator());
    }
    sb.append("    return result;").append(System.lineSeparator());
    sb.append("  }").append(System.lineSeparator());
    sb.append("}").append(System.lineSeparator());
    return sb.toString();
  }
}
