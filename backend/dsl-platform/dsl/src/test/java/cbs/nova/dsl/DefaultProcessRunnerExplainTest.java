package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultProcessRunnerExplainTest {

  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final DefaultCompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          compensationRegistry);

  private final ProcessRunner runner = new DefaultProcessRunner(transactionExecutionRepository,
          null, compensationHandler);

  @Test
  void explainModeReturnsDescriptorReportWithoutRunningExecuteWhenExplainNotSet() {
    var executed = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executed.set(true);
              return Result.success("ok");
            })
            .preview(ctx -> Result.success("preview-walk"))
            .build();
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.EXPLAIN).runId("run-explain")
            .build();

    var result = runner.run(process, ctx);

    assertThat(executed.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    var report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("P");
    assertThat(report.markdown()).isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
    assertThat(report.description()).isEmpty();
  }

  @Test
  void explainModeUsesExplainLogicWhenSet() {
    var executeCalled = new AtomicBoolean(false);
    var explainCalled = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("execute");
            })
            .preview(ctx -> Result.success("preview-walk"))
            .explain(ctx -> {
              explainCalled.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return Result.success(
                      ExplainReport.builder().name("P").description("explain").markdown("")
                              .build());
            })
            .build();
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.EXPLAIN)
            .runId("run-explain-logic").build();

    var result = runner.run(process, ctx);

    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo(
            ExplainReport.builder().name("P").description("explain").markdown("").build());
  }

  @Test
  void previewModeBypassesTemporalLauncher() {
    DslConfig.dslConfig().temporalProcessLauncher().replace(new TemporalProcessLauncher() {
      @Override
      public Result<?> launch(
              String processName,
              String taskQueue,
              Class<?> inputType,
              Class<?> outputType,
              Context<?> ctx) {
        throw new AssertionError("Temporal launcher should not be used in PREVIEW mode");
      }

      @Override
      public boolean canRun(Context<?> ctx) {
        return true;
      }
    });

    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("preview-direct"))
            .build();
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW)
            .runId("run-preview-direct").build();

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("preview-direct");

    GlobalManager.globalManager().resetForTests();
  }
}
