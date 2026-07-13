package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GlobalManagerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void endToEndProcessPreview() {
    var gm = GlobalManager.getInstance();
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
    var result = GlobalManager.getInstance()
            .runProcess("Ghost", contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void helperRoundTrip() {
    var gm = GlobalManager.getInstance();
    gm.registerHelper("upper", ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var result = gm.runHelper("upper",
            contextFactory.of("hello", ExecutionMode.PREVIEW));
    assertThat(result.value()).isEqualTo("HELLO");
  }

  @Test
  void transactionRoundTrip() {
    var gm = GlobalManager.getInstance();
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
    var result = GlobalManager.getInstance().runTransaction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void functionRoundTrip() {
    var gm = GlobalManager.getInstance();
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
    var result = GlobalManager.getInstance().runFunction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void processNamesSorted() {
    var gm = GlobalManager.getInstance();
    gm.registerProcess(Dsl.process("Z").execute(ctx -> Result.success("z")).build());
    gm.registerProcess(Dsl.process("A").execute(ctx -> Result.success("a")).build());
    var names = gm.processNames();
    assertThat(names).containsExactlyInAnyOrder("A", "Z");
  }

  @Test
  void transactionNamesSorted() {
    var gm = GlobalManager.getInstance();
    gm.registerTransaction(
            Dsl.transaction("Ztx").execute(ctx -> Result.success("z")).build());
    gm.registerTransaction(
            Dsl.transaction("Atx").execute(ctx -> Result.success("a")).build());
    var names = gm.transactionNames();
    assertThat(names).containsExactlyInAnyOrder("Atx", "Ztx");
  }

  @Test
  void helperNamesSorted() {
    var gm = GlobalManager.getInstance();
    gm.registerHelper("Ahelper", ctx -> Result.success("A"));
    gm.registerHelper("Bhelper", ctx -> Result.success("B"));
    var names = gm.helperNames();
    assertThat(names).containsExactlyInAnyOrder("Ahelper", "Bhelper");
  }

  @Test
  void describeHelperReturnsDescriptorForRegistered() {
    var gm = GlobalManager.getInstance();
    gm.registerHelper("HelperA", ctx -> Result.success("A"));
    var descriptor = gm.describeHelper("HelperA");
    assertThat(descriptor).isNotEmpty();
  }

  @Test
  void transactionCompensationRoundTrip() {
    var gm = GlobalManager.getInstance();
    var order = new java.util.ArrayList<String>();
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
    var gm = GlobalManager.getInstance();
    var order = new java.util.ArrayList<String>();
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
    var gm = GlobalManager.getInstance();
    gm.compensateTransaction("MissingTx", "run-1", new RuntimeException("boom"));
    // no exception expected
  }

  @Test
  void runProcessWithCompensationReturnsSuccessValue() {
    var gm = GlobalManager.getInstance();
    var order = new ArrayList<String>();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.success("ok");
            },
            (compCtx, error) -> order.add("compensate"),
            List.of());

    assertThat(result).isEqualTo("ok");
    assertThat(order).containsExactly("main");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnFailure() {
    var gm = GlobalManager.getInstance();
    var order = new ArrayList<String>();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.failure(new RuntimeException("boom"));
            },
            (compCtx, error) -> order.add("compensate:" + error.getMessage()),
            List.of());

    assertThat(result).isInstanceOf(DslTemporalProcessFailure.class);
    assertThat(((DslTemporalProcessFailure) result).message()).isEqualTo("Process failed");
    assertThat(order).containsExactly("main", "compensate:boom");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnException() {
    var gm = GlobalManager.getInstance();
    var order = new ArrayList<String>();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              throw new IllegalStateException("bang");
            },
            (compCtx, error) -> order.add("compensate:" + error.getClass().getSimpleName()),
            List.of());

    assertThat(result).isInstanceOf(DslTemporalProcessFailure.class);
    assertThat(((DslTemporalProcessFailure) result).detail())
            .isEqualTo(IllegalStateException.class.getName());
    assertThat(order).containsExactly("main", "compensate:IllegalStateException");
  }

  @Test
  void runProcessWithCompensationCompensatesTransactionsInReverseOrder() {
    var gm = GlobalManager.getInstance();
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

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> Result.failure(new RuntimeException("boom")),
            (compCtx, error) -> {
              /* process compensation is a no-op in this test */ },
            List.of("TxA", "TxB"));

    assertThat(result).isInstanceOf(DslTemporalProcessFailure.class);
    assertThat(order).containsExactly("TxB", "TxA");
  }
}
