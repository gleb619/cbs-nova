package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class WorkflowDefinitionGeneratorTest {

  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyWorkflow",
        "WF_1",
        DslInterfaceType.WORKFLOW,
        WF_INPUT,
        WF_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new WorkflowDefinitionGenerator(filer, s -> "return UndefinedDslObject.create();")
        .generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyWorkflowDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyWorkflowDefinition");
    String content = filer.files.get(definitionKey).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyWorkflowDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements WorkflowDefinition"), "Should implement WorkflowDefinition");
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
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyWorkflow",
        "WF_1",
        DslInterfaceType.WORKFLOW,
        WF_INPUT,
        WF_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomWorkflowDsl.workflow(\"WF_1\").build();",
        "import com.example.CustomWorkflowDsl;");

    new WorkflowDefinitionGenerator(filer, s -> s.dslBody()).generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyWorkflowDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyWorkflowDefinition");
    String content = filer.files.get(definitionKey).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomWorkflowDsl.workflow(\"WF_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomWorkflowDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.UndefinedDslObject;"),
        "Should not contain UndefinedDslObject fallback import");
  }
}
