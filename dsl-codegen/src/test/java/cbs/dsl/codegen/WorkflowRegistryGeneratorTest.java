package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class WorkflowRegistryGeneratorTest {

  @Test
  @DisplayName("shouldGenerateRegistryWithRegisterWorkflowImplementationFactoryWhenSpecsProvided")
  void shouldGenerateRegistryWithRegisterWorkflowImplementationFactoryWhenSpecsProvided()
      throws Exception {
    FakeFiler filer = new FakeFiler();
    List<EventWorkflowSpec> specs = List.of(
        new EventWorkflowSpec(
            "LOAN_SUBMIT", "com.example.LoanSubmitEvent", List.of("TX_1", "TX_2")),
        new EventWorkflowSpec("DEPOSIT_OPEN", "com.example.DepositOpenEvent", List.of("TX_3")));

    new WorkflowRegistryGenerator(filer).generate(specs);

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("GeneratedWorkflowRegistry"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No GeneratedWorkflowRegistry found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains("registerWorkflowImplementationFactory"),
        "Content should contain registerWorkflowImplementationFactory: " + content);
  }

  @Test
  @DisplayName("shouldReturnExpectedWorkflowTypesWhenSpecsProvided")
  void shouldReturnExpectedWorkflowTypesWhenSpecsProvided() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<EventWorkflowSpec> specs = List.of(
        new EventWorkflowSpec("LOAN_SUBMIT", "com.example.LoanSubmitEvent", List.of("TX_1")),
        new EventWorkflowSpec("DEPOSIT_OPEN", "com.example.DepositOpenEvent", List.of("TX_3")));

    new WorkflowRegistryGenerator(filer).generate(specs);

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("GeneratedWorkflowRegistry"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No GeneratedWorkflowRegistry found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains("public static List<String> workflowTypes()"),
        "Content should contain workflowTypes method: " + content);
    assertTrue(
        content.contains("\"LOAN_SUBMIT\""),
        "Content should contain LOAN_SUBMIT workflow type: " + content);
    assertTrue(
        content.contains("\"DEPOSIT_OPEN\""),
        "Content should contain DEPOSIT_OPEN workflow type: " + content);
  }

  @Test
  @DisplayName("shouldNotGenerateFileWhenSpecListIsEmpty")
  void shouldNotGenerateFileWhenSpecListIsEmpty() throws Exception {
    FakeFiler filer = new FakeFiler();
    new WorkflowRegistryGenerator(filer).generate(List.of());
    assertTrue(filer.files.isEmpty(), "Should not generate any files when spec list is empty");
  }
}
