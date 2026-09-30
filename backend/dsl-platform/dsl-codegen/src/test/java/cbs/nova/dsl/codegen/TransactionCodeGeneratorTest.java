package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.DslGenerated;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.transaction.GeneratedTransactionActivity;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Generated;

import java.time.Duration;
import java.util.Map;

class TransactionCodeGeneratorTest {

  private final TransactionCodeGenerator generator = new TransactionCodeGenerator(
          new DslPackageNameResolver(new CodegenNaming("cbs.nova.dsl.generated")));

  @Test
  void generatesTwoSources() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, null, null, true);
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

    var iface = generator.generate(descriptor, null, null, true).get(0);
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
    assertThat(iface.source()).contains("dslBuildInfo = \"");
    assertThat(iface.source()).contains("dslGitInfo = \"");
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

    var sources = generator.generate(descriptor, null, null, true);
    assertThat(sources.get(0).packageName())
            .isEqualTo("cbs.nova.dsl.generated.v1.loandisbursement");
  }

  @Test
  void implementationDelegatesViaGlobalManager() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null, true).get(1);
    assertThat(impl.className()).isEqualTo("LoanDisbursementTransactionDefinition");
    assertThat(impl.source()).contains("implements LoanDisbursementTransactionActivity");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().runTransactionWithCompensation(");
    assertThat(impl.source()).contains("LoanDisbursementGeneratedClassProvider");
    assertThat(impl.source()).contains("(TransactionDslObject)");
    assertThat(impl.source()).doesNotContain("runTransaction(\"");
    assertThat(impl.source()).doesNotContain("compensateTransaction(\"");
    assertThat(impl.source()).contains("request.runId()");
    assertThat(impl.source()).contains("request.payload()");
    assertThat(impl.source()).contains("DslTemporalTransactionRequest<String> request");
    assertThat(impl.source()).contains("@" + DslGenerated.class.getSimpleName());
    assertThat(impl.source()).contains("dslBuildInfo = \"");
    assertThat(impl.source()).contains("dslGitInfo = \"");
    assertThat(impl.source()).contains("@" + Generated.class.getSimpleName());
    assertThat(impl.source()).doesNotContain("ExecutionMode.RUN");
    assertThat(impl.source()).doesNotContain("TransactionRouting");
    assertThat(impl.source()).doesNotContain(".createContext(");
  }

  @Test
  void buildVersionOverridesPackageAndVersionConstant() {
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("LoanDisbursement")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var sources = generator.generate(descriptor, "9c74a34", null, true);
    assertThat(sources.get(0).packageName())
            .isEqualTo("cbs.nova.dsl.generated.v9c74a34.loandisbursement");
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

    var sources = generator.generate(descriptor, null, null, true);
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
    assertThat(impl.source()).contains("ParamTxGeneratedClassProvider");
    assertThat(impl.source()).contains("(TransactionDslObject)");
    assertThat(impl.source()).doesNotContain("runTransaction(\"");
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

    var sources = generator.generate(descriptor, null, null, true);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains(
                    "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source()).contains(
            "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().compensateTransaction(");
    assertThat(impl.source()).contains("CompensatedTxGeneratedClassProvider");
    assertThat(impl.source()).contains("(TransactionDslObject)");
    assertThat(impl.source()).doesNotContain("compensateTransaction(\"");
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

    var sources = generator.generate(descriptor, null, null, true);
    var iface = sources.get(0);
    var impl = sources.get(1);

    assertThat(iface.source())
            .contains(
                    "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source()).contains(
            "void compensate(DslTemporalTransactionRequest<String> request, Throwable error)");
    assertThat(impl.source())
            .contains("GlobalManager.globalManager().compensateTransaction(")
            .contains("NoCompTxGeneratedClassProvider")
            .contains("(TransactionDslObject)")
            .doesNotContain("compensateTransaction(\"");
  }

  @Test
  void implementationWrapsBodyInHeartbeatWhenDeclared() {
    // T750: when the DSL declares a heartbeatTimeout, the generated impl schedules a
    // periodic Temporal heartbeat around the transaction body so the activity is not
    // cancelled by Temporal for missing heartbeats.
    var tx = Dsl.transaction("HeartbeatTx")
            .input(String.class)
            .startToCloseTimeout(Duration.ofSeconds(30))
            .heartbeatTimeout(Duration.ofSeconds(5))
            .execute(ctx -> Result.success("ok"))
            .build();
    var descriptor = new DescriptorFactory().fromTransaction(tx);

    var impl = generator.generate(descriptor, null, null, true).get(1).source();

    assertThat(impl)
            .contains("import cbs.nova.starter.runtime.ActivityHeartbeat;")
            .contains("import cbs.nova.starter.runtime.ActivityHeartbeat.HeartbeatHandle;")
            .contains("ActivityHeartbeat.start(tx.heartbeatTimeout())")
            .contains("ActivityHeartbeat.stop(hb)")
            .contains("HeartbeatHandle hb = ")
            .contains("try {")
            .contains("} finally {");
  }

  @Test
  void implementationSkipsHeartbeatWhenUndeclared() {
    // T750: when no heartbeatTimeout is set, the generated impl must NOT pull in
    // ActivityHeartbeat — local execution still works and the heartbeat machinery is
    // a no-op cost-free import we can skip.
    var descriptor = new DescriptorFactory().fromTransaction(
            Dsl.transaction("PlainTx")
                    .input(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var impl = generator.generate(descriptor, null, null, true).get(1).source();

    assertThat(impl)
            .doesNotContain("ActivityHeartbeat")
            .doesNotContain("HeartbeatHandle")
            .contains("GlobalManager.globalManager().runTransactionWithCompensation(");
  }
}
