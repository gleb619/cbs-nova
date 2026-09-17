package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class CompensationExecutionModeTest {

  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final DefaultCompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          compensationRegistry);

  private final ProcessRunner runner = new DefaultProcessRunner(transactionExecutionRepository,
          null, compensationHandler);

  @Test
  void compensationBlockSeesCompensationMode() {
    var mode = new AtomicReference<ExecutionMode>();
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("fail")))
            .compensation((ctx, history) -> mode.set(ctx.mode()))
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("run-mode").build();
    runner.run(process, ctx);
    assertThat(mode.get()).isEqualTo(ExecutionMode.COMPENSATION);
  }
}
