package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class SpecificationRegistryGeneratorTest {

  @Test
  @DisplayName("shouldGenerateRegistryImplementingSpecDefinitionRegistryWhenSpecsProvided")
  void shouldGenerateRegistryImplementingSpecDefinitionRegistryWhenSpecsProvided()
      throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationModel> txSpecs = List.of(new RegistrationModel(
        "com.example",
        "TxOne",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        "cbs.dsl.api.TransactionTypes.TransactionInput",
        "cbs.dsl.api.TransactionTypes.TransactionOutput",
        DslComponentModel.SIMPLE));
    List<RegistrationModel> conditionSpecs = List.of(new RegistrationModel(
        "com.example",
        "CondOne",
        "COND_1",
        DslInterfaceType.CONDITION,
        "cbs.dsl.api.ConditionTypes.ConditionInput",
        "cbs.dsl.api.ConditionTypes.ConditionOutput",
        DslComponentModel.SIMPLE));
    List<EventWorkflowModel> eventSpecs =
        List.of(new EventWorkflowModel("EVT_1", "com.example.MyEvent", List.of("TX_1")));

    new SpecificationRegistryGenerator(filer).generate(txSpecs, conditionSpecs, eventSpecs);

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("GeneratedSpecificationRegistry"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No GeneratedSpecificationRegistry found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertNotNull(content);

    assertTrue(
        content.contains("implements SpecDefinitionRegistry"),
        "Content should implement SpecDefinitionRegistry: " + content);
    assertTrue(
        content.contains("registerActivity(\"TX_1\""),
        "Content should register TX_1 activity: " + content);
    assertTrue(
        content.contains("registerActivity(\"COND_1\""),
        "Content should register COND_1 activity: " + content);
    assertTrue(
        content.contains("registerActivity(\"EVT_1\""),
        "Content should register EVT_1 activity: " + content);
    assertTrue(
        content.contains("registerWorkflow(\"EVT_1\""),
        "Content should register EVT_1 workflow: " + content);
    assertTrue(
        content.contains("getActivityCodes()"),
        "Content should contain getActivityCodes method: " + content);
    assertTrue(
        content.contains("getWorkflowCodes()"),
        "Content should contain getWorkflowCodes method: " + content);
  }

  @Test
  @DisplayName("shouldNotGenerateFileWhenAllSpecListsAreEmpty")
  void shouldNotGenerateFileWhenAllSpecListsAreEmpty() throws Exception {
    FakeFiler filer = new FakeFiler();
    new SpecificationRegistryGenerator(filer).generate(List.of(), List.of(), List.of());
    assertTrue(
        filer.files.isEmpty(), "Should not generate any files when all spec lists are empty");
  }
}
