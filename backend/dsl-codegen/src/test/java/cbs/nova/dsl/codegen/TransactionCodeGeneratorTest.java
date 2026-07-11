package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DescriptorFactory;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.Test;

class TransactionCodeGeneratorTest {

  private final TransactionCodeGenerator generator = new TransactionCodeGenerator();

  @Test
  void generatesTwoSources() {
    var descriptor = new DescriptorFactory().fromTransaction(
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
    var descriptor = new DescriptorFactory().fromTransaction(
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
  void packageIsVersioned() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor);
    assertThat(sources.get(0).packageName())
            .isEqualTo("cbs.nova.dsl.generated.loandisbursement.v1");
  }

  @Test
  void implementationDelegatesViaGlobalManager() {
    var descriptor = new DescriptorFactory().fromTransaction(
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

  @Test
  void interfaceHasGetVersion() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("FooTx").execute(ctx -> Result.success("x")).build());
    var iface = generator.generate(descriptor).get(0);
    assertThat(iface.source()).contains("@ActivityMethod");
    assertThat(iface.source()).contains("String getVersion()");
  }

  @Test
  void implReturnsVersion() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("FooTx").version("v3").execute(ctx -> Result.success("x"))
                    .build());
    var impl = generator.generate(descriptor).get(1);
    assertThat(impl.source()).contains("\"v3\"");
    assertThat(impl.source()).contains("getVersion()");
  }

  @Test
  void implContainsTaskQueueConstant() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("FooTx").execute(ctx -> Result.success("x")).build());
    var impl = generator.generate(descriptor).get(1);
    assertThat(impl.source()).contains("TASK_QUEUE");
    assertThat(impl.source()).contains("FooTx-queue");
  }
}
