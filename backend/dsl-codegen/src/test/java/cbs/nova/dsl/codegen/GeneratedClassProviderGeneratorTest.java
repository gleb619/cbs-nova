package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.AstExtractor;
import cbs.nova.dsl.codegen.util.Json;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.model.MapInput;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Generated;

import java.util.List;
import java.util.Map;

class GeneratedClassProviderGeneratorTest {

  private final CodegenNaming naming = new CodegenNaming();
  private final GeneratedClassProviderGenerator generator = new GeneratedClassProviderGenerator(
          naming, new AstExtractor(new Json()));
  private final DescriptorFactory descriptorFactory = new DescriptorFactory();

  @Test
  void forProcessReturnsGeneratedSource() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);

    assertThat(source).isNotNull();
    assertThat(source).isInstanceOf(GeneratedSource.class);
    assertThat(source.packageName()).isEqualTo("cbs.nova.dsl.generated.loandisbursement.v1");
    assertThat(source.source()).isNotBlank();
  }

  @Test
  void forTransactionReturnsGeneratedSource() {
    var descriptor = descriptorFactory.fromTransaction(
            Dsl.transaction("ReserveInventory")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forTransaction(descriptor, null, null);

    assertThat(source).isNotNull();
    assertThat(source).isInstanceOf(GeneratedSource.class);
    assertThat(source.packageName()).isEqualTo("cbs.nova.dsl.generated.reserveinventory.v1");
    assertThat(source.source()).isNotBlank();
  }

  @Test
  void generatedSourceDeclaresFinalProviderClassImplementingContract() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);

    assertThat(source.source())
            .contains(
                    "public final class LoanDisbursementGeneratedClassProvider"
                            + " implements " + GeneratedClassProvider.class.getSimpleName());
    assertThat(source.className()).isEqualTo("LoanDisbursementGeneratedClassProvider");
  }

  @Test
  void generatedSourceDeclaresTransactionProviderClass() {
    var descriptor = descriptorFactory.fromTransaction(
            Dsl.transaction("ReserveInventory")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forTransaction(descriptor, null, null);

    assertThat(source.source())
            .contains(
                    "public final class ReserveInventoryGeneratedClassProvider"
                            + " implements " + GeneratedClassProvider.class.getSimpleName());
    assertThat(source.className()).isEqualTo("ReserveInventoryGeneratedClassProvider");
  }

  @Test
  void descriptorAccessorBodyContainsNameTypeAndTaskQueue() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);
    var body = source.source();

    assertThat(body).contains("public GeneratedClassDescriptor descriptor()");
    assertThat(body).contains("\"LoanDisbursement\"");
    assertThat(body).contains("DslObject.DslType.PROCESS");
    assertThat(body).contains("\"LoanDisbursement-queue\"");
    assertThat(body).contains("LoanDisbursementProcessWorkflow.class");
    assertThat(body).contains("LoanDisbursementProcessDefinition.class");
    assertThat(body).contains("String.class");
  }

  @Test
  void descriptorAccessorBodyContainsTransactionDescriptorFields() {
    var descriptor = descriptorFactory.fromTransaction(
            Dsl.transaction("ReserveInventory")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forTransaction(descriptor, null, null);
    var body = source.source();

    assertThat(body).contains("public GeneratedClassDescriptor descriptor()");
    assertThat(body).contains("\"ReserveInventory\"");
    assertThat(body).contains("DslObject.DslType.TRANSACTION");
    assertThat(body).contains("\"ReserveInventory-queue\"");
    assertThat(body).contains("ReserveInventoryTransactionActivity.class");
    assertThat(body).contains("ReserveInventoryTransactionDefinition.class");
  }

  @Test
  void providerClassNameMatchesCodegenNamingDerivation() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);

    var expectedClass = "LoanDisbursementGeneratedClassProvider";
    assertThat(source.className()).isEqualTo(expectedClass);
    assertThat(source.source()).contains("class " + expectedClass);
    assertThat(source.packageName()).isEqualTo(naming.versionedPackage("LoanDisbursement", "v1"));
  }

  @Test
  void buildVersionResolvesToPackageAndVersionLiteral() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, "9c74a34", null);

    assertThat(source.packageName())
            .isEqualTo("cbs.nova.dsl.generated.loandisbursement.v9c74a34");
    assertThat(source.source()).contains("\"9c74a34\"");
  }

  @Test
  void nullBuildVersionFallsBackToDescriptorVersion() {
    var descriptor = descriptorFactory.fromTransaction(
            Dsl.transaction("ReserveInventory")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forTransaction(descriptor, null, null);

    assertThat(source.packageName()).isEqualTo("cbs.nova.dsl.generated.reserveinventory.v1");
    assertThat(source.source()).contains("\"v1\"");
  }

  @Test
  void importsCollectedForReferencedTypes() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);
    var body = source.source();

    assertThat(body).contains("package cbs.nova.dsl.generated.loandisbursement.v1;");
    assertThat(body).contains("import cbs.nova.dsl.annotation.DslGenerated;");
    assertThat(body).contains("import cbs.nova.dsl.DslObject;");
    assertThat(body).contains("import cbs.nova.dsl.GeneratedClassDescriptor;");
    assertThat(body).contains("import cbs.nova.dsl.GeneratedClassProvider;");
    assertThat(body).contains("import javax.annotation.processing.Generated;");
  }

  @Test
  void importsNotDuplicatedForSameType() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("SelfReferencing")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);
    var body = source.source();

    long generatedProviderImports = countOccurrences(body,
            "import cbs.nova.dsl.GeneratedClassProvider;");
    long generatedImports = countOccurrences(body,
            "import cbs.nova.dsl.GeneratedClassDescriptor;");

    assertThat(generatedProviderImports).isEqualTo(1L);
    assertThat(generatedImports).isEqualTo(1L);
  }

  @Test
  void nonJavaLangReferenceTypesAddCanonicalImports() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("ParamProcess")
                    .parameters(reg -> reg.string("customerId"))
                    .execute(ctx -> Result.success(MapInput.fromMap(Map.of())))
                    .build());

    var source = generator.forProcess(descriptor, null, null);
    var body = source.source();

    assertThat(body).contains("import " + MapInput.class.getCanonicalName() + ";");
  }

  @Test
  void javaLangReferenceTypesAreNotImported() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);
    var body = source.source();

    assertThat(body).doesNotContain("import java.lang.String;");
    assertThat(body).doesNotContain("import java.lang.Object;");
  }

  @Test
  void carriesGeneratedAnnotationFromGeneratorMetadata() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forProcess(descriptor, null, null);

    assertThat(source.source()).contains("@" + Generated.class.getSimpleName());
  }

  @Test
  void executeAstJsonIsEmbeddedWhenPreprocessedSourceProvided() {
    var descriptor = descriptorFactory.fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var rawSource = """
            import cbs.nova.dsl.*;
            import java.util.List;

            void main() {}

            List<DslObject> define() {
              return Dsl.process("LoanDisbursement")
                  .input(String.class)
                  .output(String.class)
                  .execute(ctx -> Result.success("ok"))
                  .buildList();
            }
            """;
    var preprocessed = CompactSourcePreprocessor.preprocess("LoanDsl.java", rawSource, null);

    var source = generator.forProcess(descriptor, List.of(preprocessed.preprocessedSource()), null,
            null);

    assertThat(source.source()).contains("executeJson()");
    assertThat(source.source()).contains("LambdaExpr");
    assertThat(source.source()).contains("@" + Generated.class.getSimpleName());
  }

  private static long countOccurrences(String haystack, String needle) {
    long count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
