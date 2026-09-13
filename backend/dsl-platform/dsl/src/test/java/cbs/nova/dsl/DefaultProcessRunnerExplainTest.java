package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultProcessRunnerExplainTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final DefaultCompensationRegistry compensationRegistry = new DefaultCompensationRegistry();
  private final ProcessCompensationHandler compensationHandler = new ProcessCompensationHandler(
          contextFactory, compensationRegistry);

  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          transactionExecutionRepository, null, compensationHandler);

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
            .preview(ctx -> {
              throw new AssertionError("preview logic should not run in explain mode");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain");

    var result = runner.run(process, ctx);

    assertThat(executed.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    var report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("P");
    assertThat(report.description()).contains("**Process** `P`");
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
            .explain(ctx -> {
              explainCalled.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return Result.success(new ExplainReport("P", "explain", ""));
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain-logic");

    var result = runner.run(process, ctx);

    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo(new ExplainReport("P", "explain", ""));
  }

  @Test
  void explainModeFallbackReportRespectsMetadataBudget() {
    var process = Dsl.process("BudgetP")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var ctx = contextFactory.of("input",
            Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 20),
            ExecutionMode.EXPLAIN, "run-explain-budget");

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    var report = (ExplainReport) result.value();
    assertThat(report.description()).hasSizeLessThanOrEqualTo(20);
    assertThat(report.mermaid()).isEmpty();
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
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW, "run-preview-direct");

    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("preview-direct");

    GlobalManager.globalManager().resetForTests();
  }
}
