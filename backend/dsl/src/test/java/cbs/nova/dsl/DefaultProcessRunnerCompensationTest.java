package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class DefaultProcessRunnerCompensationTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          new DefaultCompensationRegistry());

  @Test
  void compensationRunsOnExecuteFailure() {
    var compensated = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("execute failed")))
            .compensation(ctx -> {
              compensated.set(true);
              return Result.success("compensated");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-1");
    var result = runner.run(process, ctx);
    assertThat(compensated.get()).isTrue();
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("execute failed");
  }

  @Test
  void compensationExceptionWrapsAsDslCompensationException() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("execute failed")))
            .compensation(ctx -> {
              throw new RuntimeException("compensation also failed");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-2");
    var result = runner.run(process, ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslCompensationException.class);
    assertThat(result.cause().getMessage()).contains("compensation also failed");
  }

  @Test
  void noCompensationOnSuccess() {
    var compensated = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              compensated.set(true);
              return Result.success("should not run");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-3");
    var result = runner.run(process, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(compensated.get()).isFalse();
  }
}
