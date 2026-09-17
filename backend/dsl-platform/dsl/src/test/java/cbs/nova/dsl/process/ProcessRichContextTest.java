package cbs.nova.dsl.process;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.transaction.TransactionRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class ProcessRichContextTest {

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void localRoutingRunsTransactionDirectly() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("TestTx").execute(ctx -> Result.success("local")).build());

    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    var rich = new ProcessRichContext<>(ctx);

    var result = rich.runTransaction("TestTx");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("local");
  }

  @Test
  void temporalRoutingDelegatesToRegisteredInvoker() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("TestTx").execute(ctx -> Result.success("local")).build());

    AtomicReference<String> invoked = new AtomicReference<>();
    DslConfig.dslConfig().transactionInvoker().replace(
            (name, input, ctx) -> {
              invoked.set(name);
              return Result.success("invoked:" + name);
            });

    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var rich = new ProcessRichContext<>(ctx);

    var result = rich.runTransaction("TestTx");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("invoked:TestTx");
    assertThat(invoked.get()).isEqualTo("TestTx");
  }

  @Test
  void temporalRoutingFallsBackToLocalRunnerWhenNoInvokerRegistered() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("TestTx").execute(ctx -> Result.success("fallback")).build());

    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var rich = new ProcessRichContext<>(ctx);

    var result = rich.runTransaction("TestTx");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("fallback");
  }

  @Test
  void withTransactionRoutingReturnsProcessRichContextWithUpdatedDelegate() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    var rich = new ProcessRichContext<>(ctx);
    var updated = rich.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);

    assertThat(updated).isInstanceOf(ProcessRichContext.class);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(rich.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }
}
