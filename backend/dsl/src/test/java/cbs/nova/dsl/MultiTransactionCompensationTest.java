package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class MultiTransactionCompensationTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final CompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          compensationRegistry);

  @Test
  void compensationsRunInReverseOrderAfterFailure() {
    var order = new ArrayList<String>();
    var t1 = Dsl.transaction("T1")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("t1-ok"))
            .compensation(ctx -> {
              order.add("T1-compensated");
              return Result.success(null);
            })
            .build();
    var t2 = Dsl.transaction("T2")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("t2-ok"))
            .compensation(ctx -> {
              order.add("T2-compensated");
              return Result.success(null);
            })
            .build();
    GlobalManager.globalManager().registerTransaction(t1);
    GlobalManager.globalManager().registerTransaction(t2);

    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              ctx.runTransaction("T1");
              ctx.runTransaction("T2");
              return Result.failure(new RuntimeException("boom"));
            })
            .build();

    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-lifo");
    runner.run(process, ctx);

    assertThat(order).containsExactly("T2-compensated", "T1-compensated");
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void sagaCompensationsRunInReverseOrderAfterFailure() {
    var order = new ArrayList<String>();
    var t1 = Dsl.transaction("SagaT1")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("t1-ok"))
            .compensation(ctx -> {
              order.add("SagaT1-compensated");
              return Result.success(null);
            })
            .build();
    var t2 = Dsl.transaction("SagaT2")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("t2-ok"))
            .compensation(ctx -> {
              order.add("SagaT2-compensated");
              return Result.success(null);
            })
            .build();
    GlobalManager.globalManager().registerTransaction(t1);
    GlobalManager.globalManager().registerTransaction(t2);

    var process = Dsl.process("SagaP")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              ctx.runTransaction("SagaT1");
              ctx.runTransaction("SagaT2");
              return Result.failure(new RuntimeException("boom"));
            })
            .build();

    var saga = DslSaga.create();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-saga-lifo").withSaga(saga);
    runner.run(process, ctx);

    assertThat(order).containsExactly("SagaT2-compensated", "SagaT1-compensated");
    GlobalManager.globalManager().resetForTests();
  }
}
