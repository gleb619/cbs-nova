package cbs.nova.dsl.codegen.generator;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.util.CodeWriter;
import cbs.nova.dsl.codegen.CompilerConstants;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.util.ModelTypeExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ModelRegistryGeneratorTest {

  @TempDir
  Path srcDir;
  @TempDir
  Path outDir;

  private final ModelRegistryGenerator generator = new ModelRegistryGenerator(
          new CodeWriter(), new CodegenNaming(), new ModelTypeExtractor());

  @Test
  void generatesRegistryInTargetPackageWithModelEntries() throws Exception {
    var modelsDir = Files.createDirectories(srcDir.resolve(CompilerConstants.MODELS_FOLDER));
    Files.writeString(modelsDir.resolve("TestModels.java"), """
            public class TestModels {
              public record TestIn(String value) {}
              public record TestOut(int count) {}
            }
            """);

    var result = generator.generate(srcDir, outDir, "cbs.nova.dsl.codegen.test", true);

    assertThat(result.packageName()).isEqualTo("cbs.nova.dsl.codegen.test");
    assertThat(result.className()).isEqualTo("GeneratedModelRegistry");

    var sourceFile = outDir.resolve("cbs/nova/dsl/codegen/test/GeneratedModelRegistry.java");
    assertThat(sourceFile).exists();

    var source = Files.readString(sourceFile);
    assertThat(source).contains("package cbs.nova.dsl.codegen.test;");
    assertThat(source).contains("implements ModelRegistry");
    assertThat(source).contains("TestModels.class,");
    assertThat(source).contains("TestModels.TestIn.class,");
    assertThat(source).contains("TestModels.TestOut.class");
    assertThat(source).doesNotContainPattern("\\.class\\s*,\\s*\\);");

    var serviceFile = outDir.resolve("META-INF/services/cbs.nova.dsl.registry.ModelRegistry");
    assertThat(serviceFile).exists();
    assertThat(Files.readString(serviceFile))
            .contains("cbs.nova.dsl.codegen.test.GeneratedModelRegistry");
  }

  @Test
  void generatesEmptyRegistryWhenNoModels() throws Exception {
    Files.createDirectories(srcDir.resolve(CompilerConstants.MODELS_FOLDER));

    var result = generator.generate(srcDir, outDir, "cbs.nova.dsl.codegen.empty", true);

    assertThat(result.packageName()).isEqualTo("cbs.nova.dsl.codegen.empty");

    var sourceFile = outDir.resolve("cbs/nova/dsl/codegen/empty/GeneratedModelRegistry.java");
    assertThat(sourceFile).exists();

    var source = Files.readString(sourceFile);
    assertThat(source).contains("Set.of(");
    assertThat(source).contains("modelTypes()");

    var serviceFile = outDir.resolve("META-INF/services/cbs.nova.dsl.registry.ModelRegistry");
    assertThat(serviceFile).exists();
    assertThat(Files.readString(serviceFile).trim()).isEqualTo(
            "cbs.nova.dsl.codegen.empty.GeneratedModelRegistry");
  }
}
