package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class CompensationExecutionModeTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ProcessRunner runner = new DefaultProcessRunner(
          new ExecutionTraceCollector(), contextFactory);

  @Test
  void compensationBlockSeesCompensationMode() {
    var mode = new AtomicReference<ExecutionMode>();
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("fail")))
            .compensation(ctx -> {
              mode.set(ctx.mode());
              return Result.success(null);
            })
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-mode");
    runner.run(process, ctx);
    assertThat(mode.get()).isEqualTo(ExecutionMode.COMPENSATION);
  }
}
