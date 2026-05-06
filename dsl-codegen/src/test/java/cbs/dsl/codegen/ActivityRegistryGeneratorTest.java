package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class ActivityRegistryGeneratorTest {

  @Test
  @DisplayName("shouldGenerateRegistryWithRegisterActivitiesImplementationsWhenTxAndHelperSpecsProvided")
  void shouldGenerateRegistryWithRegisterActivitiesImplementationsWhenTxAndHelperSpecsProvided() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationSpec> txSpecs = List.of(
        new RegistrationSpec(
            "com.example",
            "TxOne",
            "TX_1",
            DslInterfaceType.TRANSACTION,
            "cbs.dsl.api.TransactionTypes.TransactionInput",
            "cbs.dsl.api.TransactionTypes.TransactionOutput",
            DslComponentModel.SIMPLE));
    List<RegistrationSpec> helperSpecs = List.of(
        new RegistrationSpec(
            "com.example",
            "HelperOne",
            "H_1",
            DslInterfaceType.HELPER,
            "cbs.dsl.api.HelperTypes.HelperInput",
            "cbs.dsl.api.HelperTypes.HelperOutput",
            DslComponentModel.SIMPLE));

    new ActivityRegistryGenerator(filer).generate(txSpecs, helperSpecs);

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("GeneratedActivityRegistry"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No GeneratedActivityRegistry found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains("registerActivitiesImplementations"),
        "Content should contain registerActivitiesImplementations: " + content);
    assertTrue(
        content.contains("import cbs.dsl.codegen.generated.definitions.TxOneDefinition;"),
        "Content should contain TxOneDefinition import: " + content);
    assertTrue(
        content.contains("import cbs.dsl.codegen.generated.definitions.HelperOneDefinition;"),
        "Content should contain HelperOneDefinition import: " + content);
  }

  @Test
  @DisplayName("shouldNotGenerateFileWhenBothSpecListsAreEmpty")
  void shouldNotGenerateFileWhenBothSpecListsAreEmpty() throws Exception {
    FakeFiler filer = new FakeFiler();
    new ActivityRegistryGenerator(filer).generate(List.of(), List.of());
    assertTrue(
        filer.files.isEmpty(),
        "Should not generate any files when both spec lists are empty");
  }
}
