package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventCodeGeneratorTest {

  private static final String EV_INPUT = "cbs.dsl.api.EventTypes.EventInput";
  private static final String EV_OUTPUT = "cbs.dsl.api.EventTypes.EventOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull")
  void shouldGenerateDefinitionWithFallbackDslWhenDslBodyIsNull() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyEvent",
        "EVT_1",
        DslInterfaceType.EVENT,
        EV_INPUT,
        EV_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new EventCodeGenerator(filer).generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyEventDefinition";
    String workflowKey = "cbs.dsl.codegen.generated.Evt1Workflow";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyEventDefinition");
    assertTrue(filer.files.containsKey(workflowKey), "Should generate MyEventWorkflow");
    String content = filer.files.get(definitionKey).getContent();
    assertNotNull(content);

    assertTrue(content.contains("class MyEventDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements EventDefinition, MyEventWorkflow"),
        "Should implement EventDefinition and MyEventWorkflow");
    assertTrue(
        content.contains("return EventDsl.event(\"EVT_1\").build();"),
        "Should contain fallback dsl body");
    assertTrue(
        content.contains("import cbs.dsl.builder.EventDsl;"),
        "Should contain EventDsl import");
  }

  @Test
  @DisplayName("shouldEmbedCustomDslBodyAndImportsWhenProvided")
  void shouldEmbedCustomDslBodyAndImportsWhenProvided() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyEvent",
        "EVT_1",
        DslInterfaceType.EVENT,
        EV_INPUT,
        EV_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomEventDsl.event(\"EVT_1\").build();",
        "import com.example.CustomEventDsl;");

    new EventCodeGenerator(filer).generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyEventDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyEventDefinition");
    String content = filer.files.get(definitionKey).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomEventDsl.event(\"EVT_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomEventDsl;"),
        "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.EventDsl;"),
        "Should not contain fallback import");
  }
}
