package cbs.nova.dsl.codegen.generator;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.util.CodeWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplainResourceGeneratorTest {

  @TempDir
  Path resourcesDir;
  @TempDir
  Path outputDir;

  private final ExplainResourceGenerator generator = new ExplainResourceGenerator(
          new CodeWriter());

  @Test
  void returnsEmptyListWhenResourcesDirIsNull() throws Exception {
    var sources = generator.generate(null, outputDir, "com.example.explain");

    assertThat(sources).isEmpty();
  }

  @Test
  void returnsEmptyListWhenResourcesDirDoesNotExist() throws Exception {
    var missing = resourcesDir.resolve("does-not-exist");

    var sources = generator.generate(missing, outputDir, "com.example.explain");

    assertThat(sources).isEmpty();
  }

  @Test
  void generatesProviderClassForEachMarkdownFile() throws Exception {
    Files.writeString(resourcesDir.resolve("batch-processing.md"), """
            ---
            name: BatchProcessing
            description: sums batches
            ---

            # body""");

    var sources = generator.generate(resourcesDir, outputDir, "com.example.explain");

    assertThat(sources).hasSize(1);
    var src = sources.get(0);
    assertThat(src.packageName()).isEqualTo("com.example.explain");
    assertThat(src.className()).isEqualTo("BatchProcessingExplainResourceProvider");
    var java = outputDir.resolve(
            "com/example/explain/BatchProcessingExplainResourceProvider.java");
    assertThat(java).exists();
    var body = Files.readString(java);
    assertThat(body).contains("implements ExplainResourceProvider");
    assertThat(body).contains("\"BatchProcessing\"");
    assertThat(body).contains("\"sums batches\"");
    assertThat(body).contains("\"batch-processing.md\"");
    assertThat(body).contains("# body");
  }

  @Test
  void fallsBackToFilenameStemWhenNameMissing() throws Exception {
    Files.writeString(resourcesDir.resolve("builder-sample.md"), """
            # just a body
            """);

    var sources = generator.generate(resourcesDir, outputDir, "com.example.explain");

    var body = Files.readString(outputDir.resolve(
            "com/example/explain/BuilderSampleExplainResourceProvider.java"));
    assertThat(body).contains("\"builder-sample\"");
    assertThat(body).contains("\"builder-sample.md\"");
    assertThat(sources.get(0).className()).isEqualTo("BuilderSampleExplainResourceProvider");
  }

  @Test
  void usesBlankDescriptionWhenNotInFrontmatter() throws Exception {
    Files.writeString(resourcesDir.resolve("plain.md"), """
            ---
            name: Plain
            ---

            body""");

    generator.generate(resourcesDir, outputDir, "com.example.explain");

    var body = Files.readString(outputDir.resolve(
            "com/example/explain/PlainExplainResourceProvider.java"));
    assertThat(body).contains("private static final String DESCRIPTION = \"\";");
  }

  @Test
  void escapesFrontmatterValuesContainingQuotes() throws Exception {
    Files.writeString(resourcesDir.resolve("quoted.md"), """
            ---
            name: Quoted
            description: has a "quote"
            ---
            body""");

    generator.generate(resourcesDir, outputDir, "com.example.explain");

    var body = Files.readString(outputDir.resolve(
            "com/example/explain/QuotedExplainResourceProvider.java"));
    assertThat(body).contains("has a \\\"quote\\\"");
  }

  @Test
  void classNameNormalisesDashesAndUnderscores() {
    assertThat(ExplainResourceGenerator.providerClassName("batch-processing"))
            .isEqualTo("BatchProcessingExplainResourceProvider");
    assertThat(ExplainResourceGenerator.providerClassName("batch_processing"))
            .isEqualTo("BatchProcessingExplainResourceProvider");
    assertThat(ExplainResourceGenerator.providerClassName("simple"))
            .isEqualTo("SimpleExplainResourceProvider");
  }

  @Test
  void packageDefaultsToCanonicalBaseWhenTargetPackageBlank() throws Exception {
    Files.writeString(resourcesDir.resolve("plain.md"), """
            ---
            name: Plain
            ---
            body""");

    var sources = generator.generate(resourcesDir, outputDir, "");

    assertThat(sources.get(0).packageName()).isEqualTo("cbs.nova.dsl.generated.explain");
  }
}
