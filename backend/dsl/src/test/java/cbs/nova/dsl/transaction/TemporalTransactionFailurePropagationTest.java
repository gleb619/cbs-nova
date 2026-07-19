package cbs.nova.dsl.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemporalTransactionFailurePropagationTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void runTransactionWithCompensationThrowsOnFailure() {
    var gm = GlobalManager.globalManager();

    gm.registerTransaction(Dsl.transaction("NoCompTx")
            .execute(ctx -> Result.failure(new RuntimeException("no-comp-boom")))
            .build());

    assertThatThrownBy(() -> gm.runTransactionWithCompensation("NoCompTx", "run-1", "payload"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Transaction failed")
            .cause()
            .hasMessage("no-comp-boom");
  }

  @Test
  void compensateTransactionRunsRegisteredCompensationLogic() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    gm.registerTransaction(Dsl.transaction("CompensatedTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation((CompensationContext<String> ctx) -> {
              order.add("compensated:" + ctx.body());
              return Result.success(null);
            })
            .build());

    var baseCtx = contextFactory.of("payload", Map.of(), ExecutionMode.RUN, "run-1");
    gm.registerTransactionCompensation("CompensatedTx", "run-1", baseCtx);
    gm.compensateTransaction("CompensatedTx", "run-1", new RuntimeException("boom"));

    assertThat(order).containsExactly("compensated:payload");
  }

  @Test
  void runProcessWithCompensationThrowsAndCompensatesOnFailure() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> Result.failure(new RuntimeException("process-boom")),
            (compCtx, error) -> order.add("compensated:" + error.getMessage())))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("Process failed")
            .hasMessageContaining("process-boom");

    assertThat(order).containsExactly("compensated:process-boom");
  }

  @Test
  void runProcessWithCompensationThrowsWithoutCompensationWhenNoneConfigured() {
    var gm = GlobalManager.globalManager();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> Result.failure(new RuntimeException("uncaught-boom")),
            (compCtx, error) -> {
              /* no-op */ }))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("uncaught-boom");
  }

  @Test
  void runProcessWithCompensationReturnsSuccessValueWhenMainSucceeds() {
    var gm = GlobalManager.globalManager();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> Result.success("ok"),
            (compCtx, error) -> {
              /* no-op */ });

    assertThat(result).isEqualTo("ok");
  }
}
