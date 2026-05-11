package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class DefinitionRegistryGeneratorTest {

  @Test
  @DisplayName("shouldGenerateImplRegistrationsClassWithRegisterCalls")
  void shouldGenerateImplRegistrationsClassWithRegisterCalls() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationModel> specs = List.of(
        new RegistrationModel(
            "com.example",
            "TxOne",
            "TX_1",
            DslInterfaceType.TRANSACTION,
            "cbs.dsl.api.TransactionTypes.TransactionInput",
            "cbs.dsl.api.TransactionTypes.TransactionOutput",
            DslComponentModel.SIMPLE),
        new RegistrationModel(
            "com.example",
            "HelperOne",
            "H_1",
            DslInterfaceType.HELPER,
            "cbs.dsl.api.HelperTypes.HelperInput",
            "cbs.dsl.api.HelperTypes.HelperOutput",
            DslComponentModel.SIMPLE));

    new DefinitionRegistryGenerator(filer).generate(specs);

    assertTrue(
        filer.files.entrySet().stream()
            .anyMatch(e -> e.getKey().contains("DefinitionRegistryProviderImpl")),
        "Should contain DefinitionRegistryProviderImpl class");

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("DefinitionRegistryProviderImpl"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No DefinitionRegistryProviderImpl found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains(
            "class DefinitionRegistryProviderImpl implements DefinitionRegistryProvider"),
        "Content should contain class declaration: " + content);
    assertTrue(
        content.contains("registry.register(new TxOneDefinition(resolver))"),
        "Content should register TxOneDefinition with resolver: " + content);
    assertTrue(
        content.contains("registry.register(new HelperOneDefinition(resolver))"),
        "Content should register HelperOneDefinition with resolver: " + content);
  }

  @Test
  @DisplayName("shouldGenerateSpiServiceFile")
  void shouldGenerateSpiServiceFile() throws Exception {
    FakeFiler filer = new FakeFiler();

    new DefinitionRegistryGenerator(filer).generate(List.of());

    assertTrue(
        filer.files.entrySet().stream().anyMatch(e -> e.getKey()
            .contains("META-INF/services/cbs.dsl.api.DefinitionRegistryProvider")),
        "Should contain SPI service file");

    String spiKey = filer.files.keySet().stream()
        .filter(k -> k.contains("META-INF/services/cbs.dsl.api.DefinitionRegistryProvider"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No SPI file found"));

    String spiContent = filer.files.get(spiKey).getContent();
    assertTrue(
        spiContent.contains("cbs.dsl.codegen.generated.DefinitionRegistryProviderImpl"),
        "SPI file should contain DefinitionRegistryProviderImpl class name: " + spiContent);
  }

  @Test
  @DisplayName("shouldGenerateImplRegistrationsWhenDslBodyAndDslImportsAreNonNull")
  void shouldGenerateImplRegistrationsWhenDslBodyAndDslImportsAreNonNull() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationModel> specs = List.of(new RegistrationModel(
        "com.example",
        "TxWithBody",
        "TX_BODY",
        DslInterfaceType.TRANSACTION,
        "cbs.dsl.api.TransactionTypes.TransactionInput",
        "cbs.dsl.api.TransactionTypes.TransactionOutput",
        DslComponentModel.SIMPLE,
        "preview {} execute {} rollback {}",
        "import java.util.List;"));

    new DefinitionRegistryGenerator(filer).generate(specs);

    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("DefinitionRegistryProviderImpl"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No DefinitionRegistryProviderImpl found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains(
            "class DefinitionRegistryProviderImpl implements DefinitionRegistryProvider"),
        "Content should contain class declaration: " + content);
    assertTrue(
        content.contains("registry.register(new TxWithBodyDefinition(resolver))"),
        "Content should register TxWithBodyDefinition with resolver: " + content);
  }

  @Test
  @DisplayName("shouldGenerateSpiServiceFileWhenRegistrationsHaveDslBodyAndDslImports")
  void shouldGenerateSpiServiceFileWhenRegistrationsHaveDslBodyAndDslImports() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationModel> specs = List.of(new RegistrationModel(
        "com.example",
        "HelperWithImports",
        "H_IMP",
        DslInterfaceType.HELPER,
        "cbs.dsl.api.HelperTypes.HelperInput",
        "cbs.dsl.api.HelperTypes.HelperOutput",
        DslComponentModel.SIMPLE,
        "execute { sql {} }",
        "import java.math.BigDecimal;"));

    new DefinitionRegistryGenerator(filer).generate(specs);

    String spiKey = filer.files.keySet().stream()
        .filter(k -> k.contains("META-INF/services/cbs.dsl.api.DefinitionRegistryProvider"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No SPI file found"));

    String spiContent = filer.files.get(spiKey).getContent();
    assertTrue(
        spiContent.contains("cbs.dsl.codegen.generated.DefinitionRegistryProviderImpl"),
        "SPI file should contain DefinitionRegistryProviderImpl class name: " + spiContent);
  }
}
