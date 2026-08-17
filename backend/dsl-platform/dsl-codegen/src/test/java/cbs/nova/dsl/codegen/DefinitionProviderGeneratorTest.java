package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.generator.DefinitionProviderGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Generated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class DefinitionProviderGeneratorTest {

  @Test
  void writesProviderSourceAndSpiFile(@TempDir Path outDir) throws Exception {
    var generator = new DefinitionProviderGenerator(new CodeWriter());
    var fqcn = generator.generate(outDir, List.of("FooDsl", "BarDsl"));

    var source = outDir.resolve("GeneratedDslDefinitionProvider.java");
    assertThat(source).exists();
    var text = Files.readString(source);
    assertThat(text).contains("implements DslDefinitionProvider");
    assertThat(text).contains("sources.add(new FooDsl());");
    assertThat(text).contains("sources.add(new BarDsl());");
    assertThat(text).contains("@" + Generated.class.getSimpleName());

    var spi = outDir.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider");
    assertThat(spi).exists();
    var spiText = Files.readString(spi);
    assertThat(spiText).contains("GeneratedDslDefinitionProvider");

    assertThat(fqcn).isEqualTo("GeneratedDslDefinitionProvider");
  }

  @Test
  void emptyClassListProducesEmptyRegistrations(@TempDir Path outDir) throws Exception {
    var generator = new DefinitionProviderGenerator(new CodeWriter());
    var fqcn = generator.generate(outDir, List.of());

    var source = outDir.resolve("GeneratedDslDefinitionProvider.java");
    var text = Files.readString(source);
    assertThat(text).contains("implements DslDefinitionProvider");
    assertThat(text).doesNotContain("sources.add");
    assertThat(fqcn).isEqualTo("GeneratedDslDefinitionProvider");
  }
}
