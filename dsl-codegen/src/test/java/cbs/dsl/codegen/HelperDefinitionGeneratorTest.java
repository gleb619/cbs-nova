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

class HelperDefinitionGeneratorTest {

  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined")
  void shouldGenerateDefinitionWithUndefinedDslWhenLambdaReturnsUndefined() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null);

    HelperDefinitionGenerator gen =
        new HelperDefinitionGenerator(tempDir, s -> "UndefinedDslObject.create()");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");

    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(content.contains("class MyHelperDefinition"), "Should contain class name");
    assertTrue(
        content.contains("implements HelperDefinition"), "Should implement HelperDefinition only");
    assertTrue(
        content.contains("UndefinedDslObject.create()"),
        "Should contain UndefinedDslObject dsl body");
    assertTrue(
        content.contains("import cbs.dsl.builder.HelperDslObject;"),
        "Should contain HelperDslObject import");
    assertTrue(
        content.contains("private final MyHelper function;"),
        "Should contain function field for APT");
    assertTrue(
        content.contains("resolver.resolve(MyHelper.class)"),
        "Should resolve function class from resolver");
  }

  @Test
  @DisplayName("shouldGenerateDefinitionWithCustomDslBody")
  void shouldGenerateDefinitionWithCustomDslBody() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        "CustomHelperDsl.helper(\"HLP_1\").build()",
        "import com.example.CustomHelperDsl;");

    HelperDefinitionGenerator gen = new HelperDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("CustomHelperDsl.helper(\"HLP_1\").build()"),
        "Should contain custom dsl body");
    assertTrue(
        content.contains("import com.example.CustomHelperDsl;"),
        "Should contain custom dsl import");
  }

  @Test
  @DisplayName("shouldGenerateParameterScannerCodeForCustomInputType")
  void shouldGenerateParameterScannerCodeForCustomInputType() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        "com.example.MyHelperInput",
        "cbs.dsl.api.HelperTypes.HelperOutput",
        DslComponentModel.SIMPLE,
        null,
        null);

    HelperDefinitionGenerator gen =
        new HelperDefinitionGenerator(tempDir, s -> "UndefinedDslObject.create()");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate MyHelperDefinition");
    String content = Files.readString(definitionPath);

    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner;"), "Should import ParameterScanner");
    assertTrue(
        content.contains("import cbs.dsl.api.ParameterScanner.ParameterScanResult;"),
        "Should import ParameterScanResult");
    assertTrue(
        content.contains(
            "private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(MyHelperInput.class);"),
        "Should contain ParameterScanner static field");
    assertTrue(
        content.contains("return PARAMETERS.definitions();"), "Should return scanned parameters");
  }

  @Test
  @DisplayName("shouldGenerateSharedDslClassAndDefinitionForDslGeneratedSpec")
  void shouldGenerateSharedDslClassAndDefinitionForDslGeneratedSpec() throws Exception {
    String dslBody =
        "Dsl.helpers().helper(\"FIND_CUSTOMER_CODE\", h -> h.execute(input -> new HelperOutput(Map.of(\"customerCode\", \"CUST-\" + input.params().get(\"id\"))))).build();";

    RegistrationModel spec = new RegistrationModel(
        "",
        "BankingHelpersDsl_FindCustomerCode",
        "FIND_CUSTOMER_CODE",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        dslBody,
        "import cbs.dsl.builder.Dsl;",
        null,
        true,
        "BankingHelpersDsl");

    HelperDefinitionGenerator gen = new HelperDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    // Shared class should be generated
    Path sharedPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/BankingHelpersDslGenerated.java");
    assertTrue(Files.exists(sharedPath), "Should generate shared DSL class");
    String sharedContent = Files.readString(sharedPath);
    assertTrue(
        sharedContent.contains("class BankingHelpersDslGenerated"),
        "Shared class should have correct name");
    assertTrue(
        sharedContent.contains("public static final List<HelperDslObject> HELPERS ="),
        "Shared class should declare HELPERS field");
    assertTrue(
        sharedContent.contains("public static HelperDslObject get(String code)"),
        "Shared class should declare get method");
    assertTrue(
        sharedContent.contains(".filter(o -> code.equals(o.code()))"),
        "Shared class get method should filter by code");
    assertTrue(
        sharedContent.contains("import cbs.dsl.builder.Dsl;"),
        "Shared class should contain DSL imports");
    assertTrue(
        sharedContent.contains(dslBody.substring(0, dslBody.length() - 1)),
        "Shared class should contain DSL body without trailing semicolon");

    // Definition should reference shared class
    Path definitionPath = tempDir.resolve(
        "cbs/dsl/codegen/generated/definitions/BankingHelpersDsl_FindCustomerCodeDefinition.java");
    assertTrue(Files.exists(definitionPath), "Should generate DSL helper definition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("class BankingHelpersDsl_FindCustomerCodeDefinition"),
        "Should contain unique class name");
    assertTrue(
        content.contains("implements HelperDefinition"), "Should implement HelperDefinition");
    assertTrue(
        content.contains("private final Evaluator evaluator;"), "Should contain Evaluator field");
    assertTrue(
        content.contains(
            "public BankingHelpersDsl_FindCustomerCodeDefinition(DslComponentResolver resolver)"),
        "Should have constructor with DslComponentResolver");
    assertTrue(
        content.contains("BankingHelpersDslGenerated.get(\"FIND_CUSTOMER_CODE\")"),
        "Should call shared class get method");
    assertTrue(
        content.contains("evaluator.previewHelper(dsl, input)"),
        "Should call evaluator.previewHelper");
    assertTrue(
        content.contains("evaluator.executeHelper(dsl, input)"),
        "Should call evaluator.executeHelper");
    assertFalse(
        content.contains("private final BankingHelpersDsl_FindCustomerCode function;"),
        "Should not declare a function field for DSL-generated");
    assertFalse(
        content.contains("resolver.resolve(BankingHelpersDsl_FindCustomerCode.class)"),
        "Should not resolve a function class for DSL-generated");
    assertFalse(
        content.contains("import cbs.dsl.builder.Dsl;"),
        "Definition should not contain DSL imports (shared class has them)");
  }

  @Test
  @DisplayName("shouldGenerateMultipleDefinitionsReusingSameSharedClass")
  void shouldGenerateMultipleDefinitionsReusingSameSharedClass() throws Exception {
    String dslBody = "Dsl.helpers().helper(\"HLP_A\", h -> h).helper(\"HLP_B\", h -> h).build();";

    RegistrationModel specA = new RegistrationModel(
        "",
        "BankingHelpersDsl_HlpA",
        "HLP_A",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        dslBody,
        "import cbs.dsl.builder.Dsl;",
        null,
        true,
        "BankingHelpersDsl");

    RegistrationModel specB = new RegistrationModel(
        "",
        "BankingHelpersDsl_HlpB",
        "HLP_B",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        dslBody,
        "import cbs.dsl.builder.Dsl;",
        null,
        true,
        "BankingHelpersDsl");

    HelperDefinitionGenerator gen = new HelperDefinitionGenerator(tempDir, s -> s.dslBody());
    List<FileWrite> files = gen.generate(List.of(specA, specB));
    gen.write(files);

    // Only ONE shared class
    Path sharedPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/BankingHelpersDslGenerated.java");
    assertTrue(Files.exists(sharedPath), "Should generate exactly one shared DSL class");

    // Both definitions exist
    Path defA = tempDir.resolve(
        "cbs/dsl/codegen/generated/definitions/BankingHelpersDsl_HlpADefinition.java");
    Path defB = tempDir.resolve(
        "cbs/dsl/codegen/generated/definitions/BankingHelpersDsl_HlpBDefinition.java");
    assertTrue(Files.exists(defA), "Should generate definition A");
    assertTrue(Files.exists(defB), "Should generate definition B");

    String contentA = Files.readString(defA);
    String contentB = Files.readString(defB);

    assertTrue(
        contentA.contains("BankingHelpersDslGenerated.get(\"HLP_A\")"),
        "Definition A should call shared get method");
    assertTrue(
        contentB.contains("BankingHelpersDslGenerated.get(\"HLP_B\")"),
        "Definition B should call shared get method");
  }

  @Test
  @DisplayName("shouldGenerateUnifiedTemplateForAptGeneratedSpec")
  void shouldGenerateUnifiedTemplateForAptGeneratedSpec() throws Exception {
    RegistrationModel spec = new RegistrationModel(
        "com.example",
        "MyHelper",
        "HLP_1",
        DslInterfaceType.HELPER,
        HL_INPUT,
        HL_OUTPUT,
        DslComponentModel.SIMPLE,
        null,
        null,
        null,
        false,
        null);

    HelperDefinitionGenerator gen =
        new HelperDefinitionGenerator(tempDir, s -> "UndefinedDslObject.create()");
    List<FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path definitionPath =
        tempDir.resolve("cbs/dsl/codegen/generated/definitions/MyHelperDefinition.java");

    assertTrue(Files.exists(definitionPath), "Should generate APT helper definition");
    String content = Files.readString(definitionPath);
    assertNotNull(content);

    assertTrue(
        content.contains("private final MyHelper function;"), "Should contain function field");
    assertTrue(
        content.contains("resolver.resolve(MyHelper.class)"),
        "Should resolve function class from resolver");
    assertTrue(
        content.contains("evaluator.previewHelper(dsl, input)"),
        "Should contain evaluator fallback path");
    assertTrue(content.contains("function.preview(typed)"), "Should contain direct function call");
  }
}
