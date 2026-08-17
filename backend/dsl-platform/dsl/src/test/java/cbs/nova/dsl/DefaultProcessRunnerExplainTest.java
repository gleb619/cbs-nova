package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

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
