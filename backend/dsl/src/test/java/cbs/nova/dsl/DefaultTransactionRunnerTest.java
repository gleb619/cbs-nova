package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.junit.jupiter.api.Test;

class DefaultTransactionRunnerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  private final DefaultTransactionRunner runner = new DefaultTransactionRunner(traceCollector,
          contextFactory);

  private TransactionDslObject tx(String name) {
    return Dsl.transaction(name).execute(ctx -> Result.success("ok-" + name)).build();
  }

  @Test
  void runModeExecutesLogicAndReturnsSuccess() {
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "r1");
    var result = runner.run(tx("T"), ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok-T");
  }

  @Test
  void explainModeExecutesLogicAndReturnsSuccess() {
    var ctx = contextFactory.of("in", ExecutionMode.EXPLAIN, "r2");
    var result = runner.run(tx("T"), ctx);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void previewModeExecutesLogic() {
    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW, "r3");
    var result = runner.run(tx("T"), ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok-T");
  }

  @Test
  void thrownExceptionWrapsAsDslExecutionException() {
    var tx = Dsl.transaction("Fail")
            .execute(ctx -> {
              throw new RuntimeException("burst");
            })
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "r4");
    var result = runner.run(tx, ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    assertThat(result.cause().getMessage()).contains("burst");
  }

  @Test
  void dslExecutionExceptionPreservesRunId() {
    var tx = Dsl.transaction("Fail")
            .execute(ctx -> {
              throw new RuntimeException("err");
            })
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "my-run");
    var result = runner.run(tx, ctx);
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    assertThat(((DslException) result.cause()).runId()).isEqualTo("my-run");
  }

  @Test
  void explainModeWithPreviewLogicUsesExecuteLogic() {
    var tx = Dsl.transaction("ExplainT")
            .execute(ctx -> Result.success("execute-result"))
            .preview(ctx -> Result.success("preview-result"))
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.EXPLAIN, "r5");
    var result = runner.run(tx, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("execute-result");
  }

  @Test
  void previewModeUsesPreviewLogicWhenSet() {
    var tx = Dsl.transaction("PrevT")
            .execute(ctx -> Result.success("execute-result"))
            .preview(ctx -> Result.success("preview-result"))
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW, "r6");
    var result = runner.run(tx, ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("preview-result");
  }
}
