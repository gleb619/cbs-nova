package cbs.nova.dsl;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultHelperRunnerExplainTest {

  private final DefaultHelperRunner runner = new DefaultHelperRunner();

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
              return Result.success(ExplainReport.builder().name("explainFn").description("explain")
                      .markdown("").build());
            })
            .build());

    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.EXPLAIN)
            .runId("run-explain-fn").build();
    var result = runner.runFunction("explainFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo(
            ExplainReport.builder().name("explainFn").description("explain").markdown("").build());
    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
  }

  @Test
  void explainModeFallsBackToDescriptorReportForFunction() {
    var registry = new DefaultHelperRegistry();
    var executeCalled = new AtomicBoolean(false);
    registry.registerFunction(Dsl.function("fallbackFn")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("execute");
            })
            .build());

    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.EXPLAIN)
            .runId("run-explain-fallback-fn").build();
    var result = runner.runFunction("fallbackFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    var report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("fallbackFn");
    assertThat(report.markdown()).isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
    assertThat(report.description()).isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
  }

  @Test
  void runModeInvokesExecuteLogicForFunction() {
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(Dsl.function("runFn")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("run"))
            .explain(ctx -> Result.success(ExplainReport.builder().name("runFn")
                    .description("explain").markdown("").build()))
            .build());

    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.RUN).runId("run-mode-fn")
            .build();
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

    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.PREVIEW)
            .runId("preview-mode-fn").build();
    var result = runner.runFunction("previewFn", ctx, registry);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("preview");
  }
}
