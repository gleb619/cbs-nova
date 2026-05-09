package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class MassOperationDefinitionGeneratorTest {

  private static final String MO_INPUT = "cbs.dsl.api.MassOperationTypes.MassOperationInput";
  private static final String MO_OUTPUT = "cbs.dsl.api.MassOperationTypes.MassOperationOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyMassOp",
        "MOP_1",
        DslInterfaceType.MASS_OPERATION,
        MO_INPUT,
        MO_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new MassOperationDefinitionGenerator(filer, s -> "UndefinedDslObject.create()")
        .generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyMassOpDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyMassOpDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyMassOpDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements MassOperationDefinition"),
        "Should implement MassOperationDefinition");
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
    FakeFiler filer = new FakeFiler();
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyMassOp",
        "MOP_1",
        DslInterfaceType.MASS_OPERATION,
        MO_INPUT,
        MO_OUTPUT,
        DslComponentModel.SIMPLE,
        "CustomMassOpDsl.massOperation(\"MOP_1\").build()",
        "import com.example.CustomMassOpDsl;");

    new MassOperationDefinitionGenerator(filer, s -> s.dslBody()).generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyMassOpDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyMassOpDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("CustomMassOpDsl.massOperation(\"MOP_1\").build()"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomMassOpDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.UndefinedDslObject;"),
        "Should not contain UndefinedDslObject fallback import");
  }
}
