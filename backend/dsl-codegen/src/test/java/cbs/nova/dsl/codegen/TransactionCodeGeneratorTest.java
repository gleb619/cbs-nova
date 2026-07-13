package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslTemporalTransactionRequest;
import cbs.nova.dsl.GeneratedTransactionActivity;
import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DescriptorFactory;
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
            .contains("GlobalManager.getInstance().runTransaction(\"LoanDisbursement\"");
    assertThat(impl.source()).contains("ExecutionMode.RUN");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("request.payload()");
    assertThat(impl.source()).contains("DslTemporalTransactionRequest<String> request");
    assertThat(impl.source())
            .contains(".createContext(input, Map.of(), ExecutionMode.RUN, runId)");
    assertThat(impl.source())
            .contains(".withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY)");
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
                    .execute(ctx -> Result.success(MapInput.fromMap(java.util.Map.of())))
                    .build());

    var sources = generator.generate(descriptor, null, null);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains("Object execute(DslTemporalTransactionRequest<MapInput> request)");
    assertThat(iface.source())
            .contains("extends " + GeneratedTransactionActivity.class.getSimpleName());
    assertThat(impl.source()).contains("import " + MapInput.class.getCanonicalName() + ";");
    assertThat(impl.source()).contains("MapInput input = request.payload()");
    assertThat(impl.source())
            .contains("GlobalManager.getInstance()")
            .contains(".createContext(input, Map.of(), ExecutionMode.RUN, runId)");
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
            .contains("GlobalManager.getInstance().compensateTransaction(\"CompensatedTx\"");
    assertThat(impl.source()).doesNotContain("new CompensationRichContext");
    assertThat(impl.source()).doesNotContain("findTransaction(\"CompensatedTx\")");
  }
}
