package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslGenerated;
import cbs.nova.dsl.GeneratedTransactionActivity;
import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.config.DescriptorFactory;
import java.util.Map;
import javax.annotation.processing.Generated;
import org.junit.jupiter.api.Test;

class TransactionCodeGeneratorTest {

  private final TransactionCodeGenerator generator = new TransactionCodeGenerator(
          new CodegenNaming());

  @Test
  void generatesTwoSources() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, null, null);
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

    var iface = generator.generate(descriptor, null, null).get(0);
    assertThat(iface.className()).isEqualTo("LoanDisbursementTransactionActivity");
    assertThat(iface.source()).contains("@ActivityInterface");
    assertThat(iface.source()).contains("@ActivityMethod");
    assertThat(iface.source()).contains("interface LoanDisbursementTransactionActivity");
    assertThat(iface.source())
            .contains("extends " + GeneratedTransactionActivity.class.getSimpleName());
    assertThat(iface.source())
            .contains("Object execute(DslTemporalTransactionRequest<String> request)");
    assertThat(iface.source()).contains("namePrefix");
    assertThat(iface.source()).contains("LoanDisbursement_");
    assertThat(iface.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(iface.source()).contains("@" + Generated.class.getSimpleName());
    assertThat(iface.source())
            .contains("generator = \"" + TransactionCodeGenerator.class.getName() + "\"");
    assertThat(iface.source()).contains("String getVersion()");
    assertThat(iface.source())
            .contains(
                    "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
  }

  @Test
  void packageIsVersioned() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
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
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null).get(1);
    assertThat(impl.className()).isEqualTo("LoanDisbursementTransactionDefinition");
    assertThat(impl.source()).contains("implements LoanDisbursementTransactionActivity");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().runTransactionWithCompensation(");
    assertThat(impl.source())
            .contains("\"LoanDisbursement\", request.runId(), request.payload()");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("request.payload()");
    assertThat(impl.source()).contains("DslTemporalTransactionRequest<String> request");
    assertThat(impl.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(impl.source()).contains("@" + Generated.class.getSimpleName());
    assertThat(impl.source()).doesNotContain("ExecutionMode.RUN");
    assertThat(impl.source()).doesNotContain("TransactionRouting");
    assertThat(impl.source()).doesNotContain(".createContext(");
    assertThat(impl.source()).doesNotContain(".runTransaction(");
  }

  @Test
  void buildVersionOverridesPackageAndVersionConstant() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
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
  void parameterBasedTransactionUsesMapInput() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("ParamTx")
                    .parameters(reg -> reg.string("customerId").bool("verified"))
                    .execute(ctx -> Result.success(MapInput.fromMap(Map.of())))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains("Object execute(DslTemporalTransactionRequest<MapInput> request)");
    assertThat(iface.source())
            .contains("extends " + GeneratedTransactionActivity.class.getSimpleName());
    assertThat(impl.source()).contains("import " + MapInput.class.getCanonicalName() + ";");
    assertThat(impl.source()).contains("request.payload()");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().runTransactionWithCompensation(");
    assertThat(impl.source())
            .contains("\"ParamTx\", request.runId(), request.payload()");
    assertThat(impl.source()).doesNotContain("ExecutionMode");
    assertThat(impl.source()).doesNotContain("TransactionRouting");
  }

  @Test
  void compensationMethodUsesRequestEnvelopeAndGlobalManager() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("CompensatedTx")
                    .input(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .compensation(ctx -> Result.success("rolled back"))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains(
                    "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source()).contains(
            "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().compensateTransaction(");
    assertThat(impl.source())
            .contains("\"CompensatedTx\", request.runId(), request.payload(), error");
    assertThat(impl.source()).doesNotContain("new CompensationRichContext");
    assertThat(impl.source()).doesNotContain("findTransaction(\"CompensatedTx\")");
  }

  @Test
  void alwaysGeneratesCompensationMethodEvenWithoutCompensationBlock() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("NoCompTx")
                    .input(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains(
                    "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source()).contains(
            "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().compensateTransaction(")
            .contains("\"NoCompTx\"")
            .contains("request.runId(), request.payload(), error");
  }
}
