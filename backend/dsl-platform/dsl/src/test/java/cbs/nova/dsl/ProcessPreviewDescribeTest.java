package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class ProcessPreviewDescribeTest {

  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final DefaultCompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          compensationRegistry);

  @Test
  void processWithPreviewReturnsMockInPreviewMode() {
    var executeCalled = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("EXEC");
            })
            .preview(ctx -> Result.success("PREVIEW_MOCK"))
            .build();

    var runner = new DefaultProcessRunner(transactionExecutionRepository, null,
            compensationHandler);
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW).build();
    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("PREVIEW_MOCK");
    assertThat(executeCalled.get()).isFalse();
  }

  @Test
  void processWithoutPreviewDelegatesToExecuteInPreviewMode() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("EXEC"))
            .build();

    var runner = new DefaultProcessRunner(transactionExecutionRepository, null,
            compensationHandler);
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW).build();
    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("EXEC");
  }

  @Test
  void describeReturnsCorrectFields() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();

    DslDescriptor desc = process.descriptor();

    assertThat(desc.name()).isEqualTo("P");
    assertThat(desc.type()).isEqualTo(DslObject.DslType.PROCESS);
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.outputType()).isEqualTo(String.class);
    assertThat(desc.hasSideEffects()).isFalse();
    assertThat(desc.taskQueue()).isEqualTo("P-queue");
    assertThat(desc.version()).isEqualTo("v1");
    assertThat(desc.parameters()).isEmpty();
  }

  @Test
  void describeReportsSideEffectsWhenCompensationPresent() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation((ctx, history) -> {
            })
            .build();

    DslDescriptor desc = process.descriptor();
    assertThat(process.compensationLogic()).isNotNull();
    assertThat(desc.hasSideEffects()).isTrue();
  }

}
