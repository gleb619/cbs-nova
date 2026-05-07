package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.DslComponent.DslComponentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class TransactionCodeGeneratorTest {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyTx",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        TX_INPUT,
        TX_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    new TransactionCodeGenerator(filer, s -> "return UndefinedDslObject.create();")
        .generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyTxDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyTxDefinition");
    String content = filer.files.get(definitionKey).getContent();
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
    FakeFiler filer = new FakeFiler();
    RegistrationSpec spec = new RegistrationSpec(
        "com.example",
        "MyTx",
        "TX_1",
        DslInterfaceType.TRANSACTION,
        TX_INPUT,
        TX_OUTPUT,
        DslComponentModel.SIMPLE,
        "return CustomTxDsl.transaction(\"TX_1\").build();",
        "import com.example.CustomTxDsl;");

    new TransactionCodeGenerator(filer, s -> s.dslBody()).generate(List.of(spec));

    String definitionKey = "cbs.dsl.codegen.generated.definitions.MyTxDefinition";
    assertTrue(filer.files.containsKey(definitionKey), "Should generate MyTxDefinition");
    String content = filer.files.get(definitionKey).getContent();
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
}
