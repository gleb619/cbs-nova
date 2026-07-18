package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.util.AstExtractor;
import cbs.nova.dsl.codegen.util.Json;
import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.config.DescriptorFactory;
import java.util.List;
import javax.annotation.processing.Generated;
import org.junit.jupiter.api.Test;

class GeneratedClassProviderGeneratorTest {

  private final GeneratedClassProviderGenerator generator = new GeneratedClassProviderGenerator(
          new CodegenNaming(), new AstExtractor(new Json()));
  private final DescriptorFactory descriptorFactory = new DescriptorFactory();

  @Test
  void processProviderUsesResolvedBuildVersion() {
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
  void transactionProviderFallsBackToDescriptorVersion() {
    var descriptor = descriptorFactory.fromTransaction(
            Dsl.transaction("ReserveInventory")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var source = generator.forTransaction(descriptor, null, null);
    assertThat(source.packageName())
            .isEqualTo("cbs.nova.dsl.generated.reserveinventory.v1");
    assertThat(source.source()).contains("\"v1\"");
  }

  @Test
  void processProviderEmbedsExecuteAstJson() {
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
}
