package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class RunnerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final CompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessRunner processRunner = new DefaultProcessRunner(contextFactory,
          compensationRegistry);
  private final TransactionRunner txRunner = new DefaultTransactionRunner(contextFactory,
          compensationRegistry);
  private final HelperRunner helperRunner = new DefaultHelperRunner(contextFactory);

  @Test
  void processRunnerPreviewSuccess() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("done"))
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
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
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    processRunner.run(process, ctx);
    assertThat(compensated.get()).isTrue();
  }

  @Test
  void helperRunnerUnknownNameReturnsFailure() {
    var registry = new DefaultHelperRegistry();
    var ctx = contextFactory.of("x", ExecutionMode.PREVIEW);
    var result = helperRunner.runHelper("unknown", ctx, registry);
    assertThat(result.isSuccess()).isFalse();
  }
}
