package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowCodeGeneratorTest {

  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull")
  void shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyWf",
        "WF_1",
        DslInterfaceType.WORKFLOW,
        WF_INPUT,
        WF_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new WorkflowCodeGenerator(filer).generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyWfDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyWfDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyWfDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements WorkflowDefinition"), "Should implement WorkflowDefinition");
    assertTrue(
        content.contains("return WorkflowDsl.workflow(\"WF_1\").build();"),
        "Should contain fallback dsl body");
    assertTrue(
        content.contains("import cbs.dsl.builder.WorkflowDsl;"),
        "Should contain WorkflowDsl import");
  }

  @Test
  @DisplayName("shouldEmbedCustomDslBodyAndImportsWhenProvided")
  void shouldEmbedCustomDslBodyAndImportsWhenProvided() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyWf",
        "WF_1",
        DslInterfaceType.WORKFLOW,
        WF_INPUT,
        WF_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomWfDsl.workflow(\"WF_1\").build();",
        "import com.example.CustomWfDsl;");

    new WorkflowCodeGenerator(filer).generate(List.of(spec));

    String key = "cbs.dsl.codegen.generated.definitions.MyWfDefinition";
    assertTrue(filer.files.containsKey(key), "Should generate MyWfDefinition");
    String content = filer.files.get(key).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomWfDsl.workflow(\"WF_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomWfDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.WorkflowDsl;"),
        "Should not contain fallback import");
  }
}
