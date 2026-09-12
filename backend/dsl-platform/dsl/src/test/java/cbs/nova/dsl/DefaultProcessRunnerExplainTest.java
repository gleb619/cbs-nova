package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultProcessRunnerExplainTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private final ProcessRunner runner = new DefaultProcessRunner(contextFactory,
          new DefaultCompensationRegistry());

  @Test
  void explainModeExecutesProcessLogicAndReturnsItsResult() {
    var executed = new AtomicBoolean(false);
    var expected = Result.success("ok");
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executed.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return expected;
            })
            .preview(ctx -> {
              throw new AssertionError("preview logic should not run in explain mode");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain");

    var result = runner.run(process, ctx);

    assertThat(executed.get()).isTrue();
    assertThat(result).isSameAs(expected);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok");
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
              return Result.success("explain");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain-logic");

    var result = runner.run(process, ctx);

    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("explain");
  }

  @Test
  void explainModeFallsBackToExecuteWhenExplainNotSet() {
    var executeCalled = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return Result.success("fallback");
            })
            .build();
    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain-fallback");

    var result = runner.run(process, ctx);

    assertThat(executeCalled.get()).isTrue();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("fallback");
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
