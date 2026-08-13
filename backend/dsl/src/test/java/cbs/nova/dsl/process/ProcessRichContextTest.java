package cbs.nova.dsl.process;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.TransactionRouting;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class ProcessRichContextTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void localRoutingRunsTransactionDirectly() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("TestTx").execute(ctx -> Result.success("local")).build());

    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    var rich = new ProcessRichContext<>(ctx, contextFactory);

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

    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1")
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var rich = new ProcessRichContext<>(ctx, contextFactory);

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

    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1")
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var rich = new ProcessRichContext<>(ctx, contextFactory);

    var result = rich.runTransaction("TestTx");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("fallback");
  }

  @Test
  void withTransactionRoutingReturnsProcessRichContextWithUpdatedDelegate() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    var rich = new ProcessRichContext<>(ctx, contextFactory);
    var updated = rich.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);

    assertThat(updated).isInstanceOf(ProcessRichContext.class);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(rich.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }
}
