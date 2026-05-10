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

class ConditionDefinitionGeneratorTest {

  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
  private static final String CN_OUTPUT = "cbs.dsl.api.ConditionTypes.ConditionOutput";

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("shouldGenerateActivityInterfaceAndDefinitionWithUndefinedDsl")
  void shouldGenerateActivityInterfaceAndDefinitionWithUndefinedDsl() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyCondition",
        "COND_1",
        DslInterfaceType.CONDITION,
        CN_INPUT,
        CN_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    ConditionDefinitionGenerator gen =
        new ConditionDefinitionGenerator(tempDir, s -> "UndefinedDslObject.create()");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path activityPath =
        tempDir.resolve("cbs/dsl/codegen/generated/MyConditionActivity.java");
    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyConditionDefinition.java");

    assertTrue(Files.exists(activityPath), "Should generate MyConditionActivity");
    assertTrue(Files.exists(definitionPath), "Should generate MyConditionDefinition");

    String activityContent = Files.readString(activityPath);
    assertNotNull(activityContent);
    assertTrue(activityContent.contains("@ActivityInterface"), "Should have @ActivityInterface");
    assertTrue(
        activityContent.contains("ConditionOutput check(ConditionInput input)"),
        "Should have check method");

    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(content.contains("class MyConditionDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements ConditionDefinition, MyConditionActivity"),
        "Should implement ConditionDefinition and Activity");
    assertTrue(
        content.contains("UndefinedDslObject.create()"),
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
        "MyCondition",
        "COND_1",
        DslInterfaceType.CONDITION,
        CN_INPUT,
        CN_OUTPUT,
        DslComponentModel.SIMPLE,
        "CustomConditionDsl.condition(\"COND_1\").build()",
        "import com.example.CustomConditionDsl;");

    ConditionDefinitionGenerator gen = new ConditionDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyConditionDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyConditionDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("CustomConditionDsl.condition(\"COND_1\").build()"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomConditionDsl;"),
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
        "MyCondition",
        "COND_1",
        DslInterfaceType.CONDITION,
        "com.example.MyConditionInput",
        "cbs.dsl.api.ConditionTypes.ConditionOutput",
        DslComponentModel.SIMPLE,
        null,
        null);

    ConditionDefinitionGenerator gen =
        new ConditionDefinitionGenerator(tempDir, s -> "UndefinedDslObject.create()");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyConditionDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyConditionDefinition");
    String content = Files.readString(definitionPath);

    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner;"), "Should import ParameterScanner");
    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner.ParameterScanResult;"),
        "Should import ParameterScanResult");
    assertTrue(
        content.contains(
            "private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(MyConditionInput.class);"),
        "Should contain ParameterScanner static field");
    assertTrue(
        content.contains("return PARAMETERS.definitions();"), "Should return scanned parameters");
  }
}
