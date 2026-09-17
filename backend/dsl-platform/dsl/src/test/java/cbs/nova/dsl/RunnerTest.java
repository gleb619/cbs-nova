package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class RunnerTest {

  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final CompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          compensationRegistry);
  private final ProcessRunner processRunner = new DefaultProcessRunner(
          transactionExecutionRepository, null, compensationHandler);
  private final TransactionRunner txRunner = new DefaultTransactionRunner(compensationRegistry);
  private final HelperRunner helperRunner = new DefaultHelperRunner();

  @Test
  void processRunnerPreviewSuccess() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("done"))
            .build();
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW).build();
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
            .compensation((ctx, history) -> compensated.set(true))
            .build();
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW).build();
    processRunner.run(process, ctx);
    assertThat(compensated.get()).isTrue();
  }

  @Test
  void helperRunnerUnknownNameReturnsFailure() {
    var registry = new DefaultHelperRegistry();
    var ctx = SimpleContext.<String>builder().body("x").mode(ExecutionMode.PREVIEW).build();
    var result = helperRunner.runHelper("unknown", ctx, registry);
    assertThat(result.isSuccess()).isFalse();
  }
}
