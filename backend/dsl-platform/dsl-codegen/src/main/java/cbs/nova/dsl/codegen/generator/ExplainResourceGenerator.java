package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.escapeJavaString;

import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.CodeWriter;
import cbs.nova.dsl.explain.ExplainResourceFrontmatter;
import cbs.nova.dsl.utils.Substitutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

/**
 * Scans a directory for {@code *.md} explain resources and generates a
 * {@code ExplainResourceProvider} SPI implementation for each. The output package follows the same
 * {@code targetPackage} conventions as the other generators, so all providers land in a single
 * {@code META-INF/services} file.
 */
@Slf4j
@RequiredArgsConstructor
public final class ExplainResourceGenerator {

  private static final String SOURCE_TEMPLATE = // language=java
          """
            package ${pkg};

            import cbs.nova.dsl.explain.ExplainResourceProvider;
            import cbs.nova.dsl.annotation.DslGenerated;
            import javax.annotation.processing.Generated;
            import org.jspecify.annotations.NonNull;

            ${annotation}
            public final class ${className} implements ExplainResourceProvider {

              private static final String NAME = "${nameLiteral}";
              private static final String DESCRIPTION = "${descriptionLiteral}";
              private static final String FILENAME = "${filenameLiteral}";
              private static final String CONTENT = "${contentLiteral}";

              @Override
              public @NonNull String name() {
                return NAME;
              }

              @Override
              public @NonNull String description() {
                return DESCRIPTION;
              }

              @Override
              public @NonNull String filename() {
                return FILENAME;
              }

              @Override
              public @NonNull String content() {
                return CONTENT;
              }
            }
            """;

  private final CodeWriter codeWriter;

  /**
   * Generates one provider class per markdown file under {@code resourcesDir}. Returns the FQNs of
   * every generated provider; the caller writes the {@code META-INF/services} SPI file. Returns an
   * empty list when {@code resourcesDir} is null or does not exist.
   */
  public @NonNull List<GeneratedSource> generate(
          @NonNull Path resourcesDir,
          @NonNull Path outputDir,
          @NonNull String targetPackage) throws IOException {
    if (resourcesDir == null || !Files.isDirectory(resourcesDir)) {
      return List.of();
    }
    var providers = new ArrayList<GeneratedSource>();
    try (Stream<Path> stream = Files.walk(resourcesDir)) {
      var files = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".md"))
              .sorted()
              .toList();
      for (var file : files) {
        providers.add(generateOne(file, resourcesDir, outputDir, targetPackage));
      }
    }
    return providers;
  }

  private @NonNull GeneratedSource generateOne(
          @NonNull Path file,
          @NonNull Path resourcesDir,
          @NonNull Path outputDir,
          @NonNull String targetPackage) throws IOException {
    var raw = Files.readString(file);
    var parsed = ExplainResourceFrontmatter.parse(raw);
    var fileName = file.getFileName().toString();
    var stem = fileName.substring(0, fileName.length() - ".md".length());
    var metadata = parsed.metadata();
    var name = metadata.getOrDefault("name", stem);
    var description = metadata.getOrDefault("description", "");
    var content = parsed.body();
    var className = providerClassName(stem);

    var pkg = targetPackage.isBlank() ? "cbs.nova.dsl.generated.explain" : targetPackage;
    var annotation = GeneratorMetadata.annotation(ExplainResourceGenerator.class);
    var source = Substitutor.format(SOURCE_TEMPLATE, Map.ofEntries(
            Map.entry("pkg", pkg),
            Map.entry("annotation", annotation),
            Map.entry("className", className),
            Map.entry("nameLiteral", escapeJavaString(name)),
            Map.entry("descriptionLiteral", escapeJavaString(description)),
            Map.entry("filenameLiteral", escapeJavaString(fileName)),
            Map.entry("contentLiteral", escapeJavaString(content))));
    var dir = outputDir.resolve(pkg.replace('.', '/'));
    codeWriter.write(dir.resolve(className + ".java"), source);
    log.atLevel(Level.DEBUG)
            .log(() -> "[ExplainResourceGenerator] Wrote %s for %s".formatted(className, fileName));
    return new GeneratedSource(pkg, className, source);
  }

  static @NonNull String providerClassName(@NonNull String stem) {
    var sb = new StringBuilder(stem.length());
    var upper = true;
    for (var i = 0; i < stem.length(); i++) {
      var c = stem.charAt(i);
      if (c == '-' || c == '_' || c == ' ') {
        upper = true;
        continue;
      }
      sb.append(upper ? Character.toUpperCase(c) : c);
      upper = false;
    }
    sb.append("ExplainResourceProvider");
    return sb.toString();
  }
}
