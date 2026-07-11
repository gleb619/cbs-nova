package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.runner.DefaultProcessRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultProcessRunnerCompensationTest {

  private final ProcessRunner runner = new DefaultProcessRunner();

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
    var ctx = SimpleContext.of("input", ExecutionMode.RUN, "run-1");
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
    var ctx = SimpleContext.of("input", ExecutionMode.RUN, "run-2");
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
    var ctx = SimpleContext.of("input", ExecutionMode.RUN, "run-3");
    var result = runner.run(process, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(compensated.get()).isFalse();
  }
}
