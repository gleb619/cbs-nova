package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import cbs.dsl.codegen.DslCompiler.FileWrite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class HelperDefinitionGeneratorTest {

  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    HelperDefinitionGenerator gen =
        new HelperDefinitionGenerator(tempDir, s -> "return UndefinedDslObject.create();");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");

    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(content.contains("class MyHelperDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements HelperDefinition"),
        "Should implement HelperDefinition only");
    assertTrue(
        content.contains("return UndefinedDslObject.create();"),
        "Should contain UndefinedDslObject dsl body");
    assertTrue(
        content.contains("import cbs.dsl.builder.UndefinedDslObject;"),
        "Should contain UndefinedDslObject import");
  }

  @Test
  @DisplayName("shouldEmbedCustomDslBodyAndImportsWhenProvided")
  void shouldEmbedCustomDslBodyAndImportsWhenProvided() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomHelperDsl.helper(\"HLP_1\").build();",
        "import com.example.CustomHelperDsl;");

    HelperDefinitionGenerator gen = new HelperDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomHelperDsl.helper(\"HLP_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomHelperDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.UndefinedDslObject;"),
        "Should not contain UndefinedDslObject fallback import");
  }

  @Test
  @DisplayName("shouldGenerateParameterScannerCodeForCustomInputType")
  void shouldGenerateParameterScannerCodeForCustomInputType() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        "com.example.MyHelperInput",
        "cbs.dsl.api.HelperTypes.HelperOutput",
        DslComponentModel.SIMPLE,
        null,
        null);

    HelperDefinitionGenerator gen =
        new HelperDefinitionGenerator(tempDir, s -> "return UndefinedDslObject.create();");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);

    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner;"),
        "Should import ParameterScanner");
    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner.ParameterScanResult;"),
        "Should import ParameterScanResult");
    assertTrue(
        content.contains("private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(MyHelperInput.class);"),
        "Should contain ParameterScanner static field");
    assertTrue(
        content.contains("return PARAMETERS.definitions();"),
        "Should return scanned parameters");
  }
}
