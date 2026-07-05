package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.*;

import cbs.nova.dsl.DescriptorFactory;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.Test;

class TransactionCodeGeneratorTest {
  private final TransactionCodeGenerator generator = new TransactionCodeGenerator();

  @Test
  void generatesTwoSources() {
    var descriptor = DescriptorFactory.fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor);
    assertThat(sources).hasSize(2);
  }

  @Test
  void interfaceHasCorrectNameAndAnnotations() {
    var descriptor = DescriptorFactory.fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var iface = generator.generate(descriptor).get(0);
    assertThat(iface.className()).isEqualTo("LoanDisbursementTransactionActivity");
    assertThat(iface.source()).contains("@ActivityInterface");
    assertThat(iface.source()).contains("@ActivityMethod");
    assertThat(iface.source()).contains("interface LoanDisbursementTransactionActivity");
    assertThat(iface.source()).contains("Object execute(Object input)");
  }

  @Test
  void implementationDelegatesViaGlobalManager() {
    var descriptor = DescriptorFactory.fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor).get(1);
    assertThat(impl.className()).isEqualTo("LoanDisbursementTransactionDefinition");
    assertThat(impl.source()).contains("implements LoanDisbursementTransactionActivity");
    assertThat(impl.source())
            .contains("GlobalManager.getInstance().runTransaction(\"LoanDisbursement\"");
    assertThat(impl.source()).contains("ExecutionMode.RUN");
  }
}
