package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import cbs.dsl.codegen.DslCompiler.FileWrite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class TransactionDefinitionGeneratorTest {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";

  @TempDir
  Path tempDir;

  @Test
  @DisplayName(
      "shouldGenerateActivityInterfaceAndDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateActivityInterfaceAndDefinitionWithUndefinedDslWhenLambdaReturnsUndefined()
      throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyTx",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        TX_INPUT,
        TX_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    TransactionDefinitionGenerator gen =
        new TransactionDefinitionGenerator(tempDir, s -> "return UndefinedDslObject.create();");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path activityPath = tempDir.resolve("cbs/dsl/codegen/generated/MyTxActivity.java");
    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyTxDefinition.java");

    assertTrue(Files.exists(activityPath), "Should generate MyTxActivity");
    assertTrue(Files.exists(definitionPath), "Should generate MyTxDefinition");

    String activityContent = Files.readString(activityPath);
    assertNotNull(activityContent);
    assertTrue(
        activityContent.contains("import cbs.dsl.api.ContextTypes.ContextOutput;"),
        "Should import ContextOutput in activity interface");
    assertTrue(
        activityContent.contains("ContextOutput prepare(Map<String, Object> params)"),
        "Should contain prepare method with ContextOutput return type");
    assertTrue(
        activityContent.contains("TransactionOutput execute(TransactionInput input)"),
        "Should contain execute method");
    assertTrue(
        activityContent.contains("TransactionOutput rollback(TransactionInput input)"),
        "Should contain rollback method");

    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(content.contains("class MyTxDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements TransactionDefinition, MyTxActivity"),
        "Should implement TransactionDefinition and MyTxActivity");
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
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyTx",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        TX_INPUT,
        TX_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomTxDsl.transaction(\"TX_1\").build();",
        "import com.example.CustomTxDsl;");

    TransactionDefinitionGenerator gen = new TransactionDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyTxDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyTxDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("return CustomTxDsl.transaction(\"TX_1\").build();"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomTxDsl;"), "Should contain custom dsl import");
    assertFalse(
        content.contains("import cbs.dsl.builder.UndefinedDslObject;"),
        "Should not contain UndefinedDslObject fallback import");
  }

  @Test
  @DisplayName("shouldGenerateParameterScannerCodeForCustomInputType")
  void shouldGenerateParameterScannerCodeForCustomInputType() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyTx",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        "com.example.MyTxInput",
        TX_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    TransactionDefinitionGenerator gen =
        new TransactionDefinitionGenerator(tempDir, s -> "return UndefinedDslObject.create();");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyTxDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyTxDefinition");
    String content = Files.readString(definitionPath);

    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner;"),
        "Should import ParameterScanner");
    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner.ParameterScanResult;"),
        "Should import ParameterScanResult");
    assertTrue(
        content.contains("private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(MyTxInput.class);"),
        "Should contain ParameterScanner static field");
    assertTrue(
        content.contains("return PARAMETERS.definitions();"),
        "Should return scanned parameters");
  }
}
