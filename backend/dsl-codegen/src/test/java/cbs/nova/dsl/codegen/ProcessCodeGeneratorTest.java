package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DescriptorFactory;
import org.junit.jupiter.api.Test;

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
    assertThat(iface.source()).contains(
            "interface LoanDisbursementProcessWorkflow extends "
                    + DslTemporalProcess.class.getSimpleName());
    assertThat(iface.source())
            .contains("Object execute(" + DslTemporalProcessRequest.class.getSimpleName()
                    + " request)");
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
            .contains(DslTemporalProcessRequest.class.getSimpleName() + " request");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("request.payload()");
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
    assertThat(impl.source()).contains("LoanDisbursement-compensation");
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
  void implContainsTaskQueueConstant() {
    var descriptor = descriptor().fromProcess(
            Dsl.process("Foo").execute(ctx -> Result.success("x")).build());
    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.source()).contains("TASK_QUEUE");
    assertThat(impl.source()).contains("Foo-queue");
  }
}
