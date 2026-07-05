package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class RunnerTest {
  private final ProcessRunner processRunner = new DefaultProcessRunner();
  private final TransactionRunner txRunner = new DefaultTransactionRunner();
  private final HelperRunner helperRunner = new DefaultHelperRunner();

  @Test
  void processRunnerPreviewSuccess() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("done"))
            .build();
    var ctx = SimpleContext.of("input", ExecutionMode.PREVIEW);
    var result = processRunner.run(process, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("done");
  }

  @Test
  void processRunnerCompensatesOnFailure() {
    var compensated = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("fail")))
            .compensation(
                    ctx -> {
                      compensated.set(true);
                      return Result.success(null);
                    })
            .build();
    var ctx = SimpleContext.of("input", ExecutionMode.PREVIEW);
    processRunner.run(process, ctx);
    assertThat(compensated.get()).isTrue();
  }

  @Test
  void helperRunnerUnknownNameReturnsFailure() {
    var registry = new DefaultHelperRegistry();
    var ctx = SimpleContext.of("x", ExecutionMode.PREVIEW);
    var result = helperRunner.runHelper("unknown", ctx, registry);
    assertThat(result.isSuccess()).isFalse();
  }
}
