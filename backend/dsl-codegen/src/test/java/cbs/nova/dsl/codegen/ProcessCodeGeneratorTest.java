package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionRouting;
import cbs.nova.dsl.config.DescriptorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

class ProcessCodeGeneratorTest {

  private final ProcessCodeGenerator generator = new ProcessCodeGenerator(new CodegenNaming());

  private static DescriptorFactory descriptor() {
    return new DescriptorFactory();
  }

  @Test
  void generatesTwoSources() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    assertThat(sources).hasSize(2);
  }

  @Test
  void interfaceHasCorrectNameAndAnnotations() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var iface = generator.generate(descriptor, null, null).get(0);
    assertThat(iface.className()).isEqualTo("LoanDisbursementProcessWorkflow");
    assertThat(iface.source()).contains("@WorkflowInterface");
    assertThat(iface.source()).contains("@WorkflowMethod");
    assertThat(iface.source())
            .contains("interface LoanDisbursementProcessWorkflow extends "
                    + DslTemporalProcess.class.getSimpleName() + "<String>");
    assertThat(iface.source())
            .contains("Object execute(" + DslTemporalProcessRequest.class.getSimpleName()
                    + "<String> request)");
  }

  @Test
  void packageIsVersioned() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    assertThat(sources.get(0).packageName())
            .isEqualTo("cbs.nova.dsl.generated.loandisbursement.v1");
  }

  @Test
  void implementationDelegatesViaGlobalManager() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.className()).isEqualTo("LoanDisbursementProcessDefinition");
    assertThat(impl.source()).contains("implements LoanDisbursementProcessWorkflow");
    assertThat(impl.source())
            .contains("GlobalManager.getInstance().runProcess(\"LoanDisbursement\"");
    assertThat(impl.source()).contains("ExecutionMode.RUN");
    assertThat(impl.source())
            .contains("TransactionRouting.TEMPORAL_ACTIVITY");
    assertThat(impl.source()).doesNotContain("java.lang.reflect.Method");
    assertThat(impl.source()).doesNotContain("class TemporalTransactionInvoker");
    assertThat(impl.source()).doesNotContain("dsl.transaction.invoker");
    assertThat(impl.source()).doesNotContain("GlobalManager.getInstance().transactionInvoker()");
    assertThat(impl.source())
            .contains(DslTemporalProcessRequest.class.getSimpleName() + "<String> request");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("String input = request.payload()");
  }

  @Test
  void withCompensationEmitsSagaCode() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .compensation(ctx -> Result.success(null))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("Saga");
    assertThat(impl.source()).contains("saga.addCompensation");
    assertThat(impl.source()).contains("saga.compensate()");
    assertThat(impl.source()).contains("process.compensationLogic()");
  }

  @Test
  void withoutCompensationUsesDefaultNoOpCompensation() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("Saga");
    assertThat(impl.source()).contains("saga.addCompensation");
    assertThat(impl.source()).contains("saga.compensate()");
    assertThat(impl.source()).contains("default no-op compensation");
    assertThat(impl.source()).doesNotContain("LoanDisbursement-compensation");
  }

  @Test
  void interfaceHasGetVersion() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("Foo").execute(ctx -> Result.success("x")).build());
    var iface = generator.generate(descriptor, null, null).get(0);
    assertThat(iface.source()).contains("@QueryMethod");
    assertThat(iface.source()).contains("String getVersion()");
  }

  @Test
  void implReturnsVersion() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("Foo").version("v2").execute(ctx -> Result.success("x"))
                    .build());
    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("\"v2\"");
    assertThat(impl.source()).contains("getVersion()");
  }

  @Test
  void implDoesNotContainLegacyTaskQueueConstant() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("Foo").execute(ctx -> Result.success("x")).build());
    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).doesNotContain("TASK_QUEUE");
    assertThat(impl.source()).doesNotContain("Foo-queue");
  }

  @Test
  void parameterBasedProcessUsesMapInput() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("ParamProcess")
                    .parameters(reg -> reg.string("customerId").number("amount"))
                    .execute(ctx -> Result.success(MapInput.fromMap(java.util.Map.of())))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source()).contains("extends DslTemporalProcess<MapInput>");
    assertThat(iface.source()).contains("DslTemporalProcessRequest<MapInput> request");
    assertThat(impl.source()).contains("import " + MapInput.class.getCanonicalName() + ";");
    assertThat(impl.source()).contains("MapInput input = request.payload()");
    assertThat(impl.source())
            .contains("GlobalManager.getInstance()")
            .contains(".createContext(input, Map.of(), ExecutionMode.RUN, runId)");
    assertThat(impl.source())
            .contains(".withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY)");
  }

  @Test
  void buildVersionOverridesPackageAndVersionConstant() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, "9c74a34", null);
    assertThat(sources.get(0).packageName())
            .isEqualTo("cbs.nova.dsl.generated.loandisbursement.v9c74a34");
    assertThat(sources.get(1).source())
            .contains("VERSION = \"9c74a34\"");
  }

  @Test
  void withTransactionsEmitsPerTransactionCompensationMethods() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .transactions(List.of("ReserveInventory", "ChargePayment"))
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("compensateReserveInventory");
    assertThat(impl.source()).contains("compensateChargePayment");
    assertThat(impl.source()).contains("saga.addCompensation(() -> compensateReserveInventory");
    assertThat(impl.source()).contains("saga.addCompensation(() -> compensateChargePayment");
  }
}
