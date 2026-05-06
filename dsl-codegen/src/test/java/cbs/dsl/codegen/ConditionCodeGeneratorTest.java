package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConditionCodeGeneratorTest {

  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
  private static final String CN_OUTPUT = "cbs.dsl.api.ConditionTypes.ConditionOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull")
  void shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyCondition",
        "COND_1",
        DslInterfaceType.CONDITION,
        CN_INPUT,
        CN_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new ConditionCodeGenerator(filer).generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyConditionDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyConditionDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyConditionDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements ConditionDefinition"), "Should implement ConditionDefinition");
    assertTrue(
        content.contains("return ConditionDsl.condition(\"COND_1\").build();"),
        "Should contain fallback dsl body");
    assertTrue(
        content.contains("import cbs.dsl.builder.ConditionDsl;"),
        "Should contain ConditionDsl import");
  }

  @Test
  @DisplayName("shouldEmbedCustomDslBodyAndImportsWhenProvided")
  void shouldEmbedCustomDslBodyAndImportsWhenProvided() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyCondition",
        "COND_1",
        DslInterfaceType.CONDITION,
        CN_INPUT,
        CN_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomConditionDsl.condition(\"COND_1\").build();",
        "import com.example.CustomConditionDsl;");

    new ConditionCodeGenerator(filer).generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyConditionDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyConditionDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomConditionDsl.condition(\"COND_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomConditionDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.ConditionDsl;"),
        "Should not contain fallback import");
  }
}
