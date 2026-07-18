package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class DefaultTransactionRunnerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private final CompensationRegistry compensationRegistry = new CompensationRegistry();

  private final DefaultTransactionRunner runner = new DefaultTransactionRunner(traceCollector,
          contextFactory, compensationRegistry);

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

  @Test
  void registersCompensationInRegistryOnSuccess() {
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("RegTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("compensated:" + ctx.body());
              return Result.success(null);
            })
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "r-reg");

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(compensationRegistry.hasCompensation("r-reg")).isTrue();
    compensationRegistry.compensateAll("r-reg", new RuntimeException("boom"), traceCollector,
            contextFactory);
    assertThat(order).containsExactly("compensated:in");
  }

  @Test
  void registersCompensationInSagaOnSuccess() {
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("SagaTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("compensated:" + ctx.body());
              return Result.success(null);
            })
            .build();
    var saga = DslSaga.create();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "r-saga").withSaga(saga);

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(saga.hasCompensations()).isTrue();
    saga.compensate();
    assertThat(order).containsExactly("compensated:in");
  }

  @Test
  void skipsCompensationRegistrationWhenCompensationMissing() {
    var tx = Dsl.transaction("NoCompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    var ctx = contextFactory.of("in", ExecutionMode.RUN, "r-no-comp");

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(compensationRegistry.hasCompensation("r-no-comp")).isFalse();
  }
}
