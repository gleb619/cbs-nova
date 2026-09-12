package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultHelperRunnerExplainTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final DefaultHelperRunner runner = new DefaultHelperRunner(contextFactory);

  @Test
  void explainModeInvokesFunctionExplainLogic() {
    var registry = new DefaultHelperRegistry();
    var executeCalled = new AtomicBoolean(false);
    var explainCalled = new AtomicBoolean(false);
    registry.registerFunction(Dsl.function("explainFn")
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
            .build());

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain-fn");
    var result = runner.runFunction("explainFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("explain");
    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
  }

  @Test
  void explainModeFallsBackToExecuteForFunction() {
    var registry = new DefaultHelperRegistry();
    var executeCalled = new AtomicBoolean(false);
    registry.registerFunction(Dsl.function("fallbackFn")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return Result.success("execute");
            })
            .build());

    var ctx = contextFactory.of("input", ExecutionMode.EXPLAIN, "run-explain-fallback-fn");
    var result = runner.runFunction("fallbackFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("execute");
    assertThat(executeCalled.get()).isTrue();
  }

  @Test
  void runModeInvokesExecuteLogicForFunction() {
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(Dsl.function("runFn")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("run"))
            .explain(ctx -> Result.success("explain"))
            .build());

    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-mode-fn");
    var result = runner.runFunction("runFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("run");
  }

  @Test
  void previewModeInvokesPreviewLogicForFunction() {
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(Dsl.function("previewFn")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("execute"))
            .preview(ctx -> Result.success("preview"))
            .build());

    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW, "preview-mode-fn");
    var result = runner.runFunction("previewFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("preview");
  }
}
