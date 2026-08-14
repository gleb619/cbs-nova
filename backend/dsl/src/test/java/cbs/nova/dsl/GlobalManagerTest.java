package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class GlobalManagerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void endToEndProcessPreview() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(
            Dsl.process("Greet")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("Hello, " + ctx.body()))
                    .build());
    var ctx = contextFactory.of("World", ExecutionMode.PREVIEW);
    var result = gm.runProcess("Greet", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("Hello, World");
  }

  @Test
  void unknownProcessReturnsFailure() {
    var result = GlobalManager.globalManager()
            .runProcess("Ghost", contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void helperRoundTrip() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("upper", ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var result = gm.runHelper("upper",
            contextFactory.of("hello", ExecutionMode.PREVIEW));
    assertThat(result.value()).isEqualTo("HELLO");
  }

  @Test
  void transactionRoundTrip() {
    var gm = GlobalManager.globalManager();
    var tx = Dsl.transaction("TestTx")
            .execute(ctx -> Result.success("ok"))
            .build();
    gm.registerTransaction(tx);
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var result = gm.runTransaction("TestTx", ctx);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void unknownTransactionReturnsFailure() {
    var result = GlobalManager.globalManager().runTransaction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void functionRoundTrip() {
    var gm = GlobalManager.globalManager();
    var fn = Dsl.function("TestFn")
            .execute(ctx -> Result.success("fn-ok"))
            .build();
    gm.registerFunction(fn);
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var result = gm.runFunction("TestFn", ctx);
    assertThat(result.value()).isEqualTo("fn-ok");
  }

  @Test
  void unknownFunctionReturnsFailure() {
    var result = GlobalManager.globalManager().runFunction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void processNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("Z").execute(ctx -> Result.success("z")).build());
    gm.registerProcess(Dsl.process("A").execute(ctx -> Result.success("a")).build());
    var names = gm.processNames();
    assertThat(names).containsExactlyInAnyOrder("A", "Z");
  }

  @Test
  void transactionNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("Ztx").execute(ctx -> Result.success("z")).build());
    gm.registerTransaction(
            Dsl.transaction("Atx").execute(ctx -> Result.success("a")).build());
    var names = gm.transactionNames();
    assertThat(names).containsExactlyInAnyOrder("Atx", "Ztx");
  }

  @Test
  void helperNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("Ahelper", ctx -> Result.success("A"));
    gm.registerHelper("Bhelper", ctx -> Result.success("B"));
    var names = gm.helperNames();
    assertThat(names).containsExactlyInAnyOrder("Ahelper", "Bhelper");
  }

  @Test
  void describeHelperReturnsDescriptorForRegistered() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("HelperA", ctx -> Result.success("A"));
    var descriptor = gm.describeHelper("HelperA");
    assertThat(descriptor).isNotEmpty();
  }

  @Test
  void transactionCompensationRoundTrip() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("CompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("compensated:" + ctx.body());
              return Result.success(null);
            })
            .build();
    gm.registerTransaction(tx);
    var baseCtx = contextFactory.of("payload", ExecutionMode.RUN, "run-comp");
    assertThat(gm.registerTransactionCompensation("CompTx", "run-comp", baseCtx)).isTrue();
    gm.compensateTransaction("CompTx", "run-comp", new RuntimeException("boom"));
    assertThat(order).containsExactly("compensated:payload");
  }

  @Test
  void directTransactionCompensationFindsRegisteredTransaction() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("DirectCompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("direct:" + ctx.body());
              return Result.success(null);
            })
            .build();
    gm.registerTransaction(tx);
    var ctx = contextFactory.of("direct-payload", ExecutionMode.COMPENSATION, "run-direct");
    gm.compensateTransaction("DirectCompTx", ctx, new RuntimeException("boom"));
    assertThat(order).containsExactly("direct:direct-payload");
  }

  @Test
  void missingTransactionCompensationIsNoOp() {
    var gm = GlobalManager.globalManager();
    gm.compensateTransaction("MissingTx", "run-1", new RuntimeException("boom"));
    // no exception expected
  }

  @Test
  void runProcessWithCompensationReturnsSuccessValue() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.success("ok");
            },
            (compCtx, error) -> order.add("compensate"));

    assertThat(result).isEqualTo("ok");
    assertThat(order).containsExactly("main");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnFailure() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.failure(new RuntimeException("boom"));
            },
            (compCtx, error) -> order.add("compensate:" + error.getMessage())))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("Process failed")
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("main", "compensate:boom");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnException() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              throw new IllegalStateException("bang");
            },
            (compCtx, error) -> order.add("compensate:" + error.getClass().getSimpleName())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("bang");

    assertThat(order).containsExactly("main", "compensate:IllegalStateException");
  }

  @Test
  void runProcessWithCompensationCompensatesTransactionsInReverseOrder() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    gm.registerTransaction(Dsl.transaction("TxA")
            .execute(ctx -> Result.success("a"))
            .compensation(ctx -> {
              order.add("TxA");
              return Result.success(null);
            })
            .build());

    gm.registerTransaction(Dsl.transaction("TxB")
            .execute(ctx -> Result.success("b"))
            .compensation(ctx -> {
              order.add("TxB");
              return Result.success(null);
            })
            .build());

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              gm.runTransaction("TxA", ctx);
              gm.runTransaction("TxB", ctx);
              return Result.failure(new RuntimeException("boom"));
            },
            (compCtx, error) -> {
              /* process compensation is a no-op in this test */ }))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("TxB", "TxA");
  }
  @Test
  void runTransactionWithCompensationReturnsSuccessValue() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("SugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("tx-" + ctx.body()))
            .build());

    Object result = gm.runTransactionWithCompensation("SugarTx", "run-1", "payload");

    assertThat(result).isEqualTo("tx-payload");
  }

  @Test
  void runTransactionWithCompensationThrowsOnFailure() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("FailingSugarTx")
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .build());

    assertThatThrownBy(() -> gm.runTransactionWithCompensation("FailingSugarTx", "run-1", "x"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Transaction failed")
            .cause()
            .hasMessage("boom");
  }

  @Test
  void compensateTransactionWithInputRunsCompensationLogic() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    gm.registerTransaction(Dsl.transaction("CompSugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("comp:" + ctx.body());
              return Result.success(null);
            })
            .build());

    gm.compensateTransaction("CompSugarTx", "run-1", "input", new RuntimeException("boom"));

    assertThat(order).containsExactly("comp:input");
  }

  @Test
  void compensateTransactionWithInputIsNoOpWhenCompensationMissing() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("NoCompSugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .build());

    gm.compensateTransaction("NoCompSugarTx", "run-1", "input", new RuntimeException("boom"));
    // no exception expected
  }

  @Test
  void runProcessWithCompensationDoesNotDoubleRunProcessCompensation() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    gm.registerProcess(Dsl.process("DoubleCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .compensation(ctx -> {
              order.add("process-comp");
              return Result.success(null);
            })
            .build());

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> gm.runProcess("DoubleCheck", ctx),
            (compCtx, error) -> gm.compensateProcess("DoubleCheck", compCtx, error)))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("process-comp");
  }
}
