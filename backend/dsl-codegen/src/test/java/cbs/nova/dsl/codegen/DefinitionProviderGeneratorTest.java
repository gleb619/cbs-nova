package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

class DefinitionProviderGeneratorTest {

  @Test
  void writesProviderSourceAndSpiFile(@TempDir Path outDir) throws Exception {
    var generator = new DefinitionProviderGenerator(Level.INFO);
    var fqcn = generator.generate(outDir, List.of("FooDsl", "BarDsl"));

    var source = outDir.resolve("GeneratedDslDefinitionProvider.java");
    assertThat(source).exists();
    var text = Files.readString(source);
    assertThat(text).contains("implements DslDefinitionProvider");
    assertThat(text).contains("new FooDsl().define()");
    assertThat(text).contains("new BarDsl().define()");

    var spi = outDir.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider");
    assertThat(spi).exists();
    var spiText = Files.readString(spi);
    assertThat(spiText).contains("GeneratedDslDefinitionProvider");

    assertThat(fqcn).isEqualTo("GeneratedDslDefinitionProvider");
  }

  @Test
  void emptyClassListProducesEmptyRegistrations(@TempDir Path outDir) throws Exception {
    var generator = new DefinitionProviderGenerator(Level.INFO);
    var fqcn = generator.generate(outDir, List.of());

    var source = outDir.resolve("GeneratedDslDefinitionProvider.java");
    var text = Files.readString(source);
    assertThat(text).contains("implements DslDefinitionProvider");
    assertThat(text).doesNotContain("addAll");
    assertThat(fqcn).isEqualTo("GeneratedDslDefinitionProvider");
  }
}
