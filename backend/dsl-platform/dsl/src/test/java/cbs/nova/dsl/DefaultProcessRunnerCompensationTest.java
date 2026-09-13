package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslCompensationException;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class DefaultProcessRunnerCompensationTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final DefaultCompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          contextFactory, compensationRegistry);

  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          compensationRegistry, transactionExecutionRepository, null, compensationHandler);

  @Test
  void compensationRunsOnExecuteFailure() {
    var compensated = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("execute failed")))
            .compensation((ctx, history) -> compensated.set(true))
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
            .compensation((ctx, history) -> {
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
            .compensation((ctx, history) -> compensated.set(true))
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-3");
    var result = runner.run(process, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(compensated.get()).isFalse();
  }
}
