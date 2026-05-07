package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class HelperCodeGeneratorTest {

  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new HelperCodeGenerator(filer, s -> "return UndefinedDslObject.create();")
        .generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyHelperDefinition";
    String activityKey = "cbs.dsl.codegen.generated.MyHelperActivity";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyHelperDefinition");
    assertTrue(filer.files.containsKey(activityKey), "Should generate MyHelperActivity");
    String content = filer.files.get(definitionKey).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyHelperDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements HelperDefinition, MyHelperActivity"),
        "Should implement HelperDefinition and MyHelperActivity");
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
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomHelperDsl.helper(\"HLP_1\").build();",
        "import com.example.CustomHelperDsl;");

    new HelperCodeGenerator(filer, s -> s.dslBody()).generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyHelperDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyHelperDefinition");
    String content = filer.files.get(definitionKey).getContent();
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
}
