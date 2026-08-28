package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.DslGenerated;
import cbs.nova.dsl.codegen.generator.ProcessCodeGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.process.DslTemporalProcess;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.dsl.process.ProcessCompensation;
import cbs.nova.dsl.process.ProcessMain;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Generated;

import java.util.List;
import java.util.Map;

class ProcessCodeGeneratorTest {

  private final ProcessCodeGenerator generator = new ProcessCodeGenerator(
          new DslPackageNameResolver(new CodegenNaming()));

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
    assertThat(iface.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(iface.source()).contains("@" + Generated.class.getSimpleName());
    assertThat(iface.source())
            .contains("generator = \"" + ProcessCodeGenerator.class.getName() + "\"");
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
            .contains("GlobalManager.globalManager().runProcessWithCompensation(");
    assertThat(impl.source()).contains(ProcessMain.class.getSimpleName());
    assertThat(impl.source()).contains(ProcessCompensation.class.getSimpleName());
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().runProcess(\"LoanDisbursement\"");
    assertThat(impl.source()).doesNotContain("java.lang.reflect.Method");
    assertThat(impl.source()).doesNotContain("class TemporalTransactionInvoker");
    assertThat(impl.source()).doesNotContain("dsl.transaction.invoker");
    assertThat(impl.source()).doesNotContain("GlobalManager.globalManager().transactionInvoker()");
    assertThat(impl.source())
            .contains(DslTemporalProcessRequest.class.getSimpleName() + "<String> request");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("String input = request.payload()");
    assertThat(impl.source()).doesNotContain("ExecutionMode");
    assertThat(impl.source()).doesNotContain("TransactionRouting");
    assertThat(impl.source()).doesNotContain("Saga");
    assertThat(impl.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(impl.source()).contains("@" + Generated.class.getSimpleName());
    assertThat(impl.source())
            .contains("generator = \"" + ProcessCodeGenerator.class.getName() + "\"");
  }

  @Test
  void withCompensationEmitsFunctionalCompensation() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .compensation(ctx -> Result.success(null))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("runProcessWithCompensation");
    assertThat(impl.source()).contains(ProcessCompensation.class.getSimpleName());
    assertThat(impl.source()).contains("compensateProcess");
    assertThat(impl.source()).doesNotContain("Saga");
    assertThat(impl.source()).doesNotContain("saga.addCompensation");
    assertThat(impl.source()).doesNotContain("saga.compensate()");
  }

  @Test
  void withoutCompensationStillUsesCompensateProcessLambda() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("runProcessWithCompensation");
    assertThat(impl.source()).contains(ProcessCompensation.class.getSimpleName());
    assertThat(impl.source()).contains("compensateProcess");
    assertThat(impl.source()).doesNotContain("Saga");
    assertThat(impl.source()).doesNotContain("saga.addCompensation");
    assertThat(impl.source()).doesNotContain("saga.compensate()");
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
                    .execute(ctx -> Result.success(MapInput.fromMap(Map.of())))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source()).contains("extends DslTemporalProcess<MapInput>");
    assertThat(iface.source()).contains("DslTemporalProcessRequest<MapInput> request");
    assertThat(impl.source()).contains("import " + MapInput.class.getCanonicalName() + ";");
    assertThat(impl.source()).contains("MapInput input = request.payload()");
    assertThat(impl.source()).contains("runProcessWithCompensation");
    assertThat(impl.source()).doesNotContain("ExecutionMode");
    assertThat(impl.source()).doesNotContain("TransactionRouting");
    assertThat(impl.source()).doesNotContain("Saga");
    assertThat(impl.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(impl.source()).contains("@" + Generated.class.getSimpleName());
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
  void withTransactionsPassesTransactionRefsAsList() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .transactions(List.of("ReserveInventory", "ChargePayment"))
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("runProcessWithCompensation");
    assertThat(impl.source()).doesNotContain("registerTransactionCompensation");
    assertThat(impl.source()).doesNotContain("compensateTransaction");
    assertThat(impl.source()).doesNotContain("private void compensateReserveInventory");
    assertThat(impl.source()).doesNotContain("private void compensateChargePayment");
    assertThat(impl.source()).doesNotContain("Saga");
  }
}
