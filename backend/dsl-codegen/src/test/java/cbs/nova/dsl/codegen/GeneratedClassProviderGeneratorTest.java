package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DescriptorFactory;
import org.junit.jupiter.api.Test;

class GeneratedClassProviderGeneratorTest {

  private final GeneratedClassProviderGenerator generator = new GeneratedClassProviderGenerator(
          new CodegenNaming());
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
}
