package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class MultiTransactionCompensationTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ProcessRunner runner = new DefaultProcessRunner(
          new ExecutionTraceCollector(), contextFactory);

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
}
