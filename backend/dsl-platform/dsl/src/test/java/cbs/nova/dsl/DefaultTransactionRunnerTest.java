package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.exception.DslException;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.listener.DefaultExecutionListener;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionExecutionStatus;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultTransactionRunnerTest {

  private final CompensationRegistry compensationRegistry = new DefaultCompensationRegistry();

  private final DefaultTransactionRunner runner = new DefaultTransactionRunner(
          compensationRegistry);

  private TransactionDslObject tx(String name) {
    return Dsl.transaction(name).execute(ctx -> Result.success("ok-" + name)).build();
  }

  @Test
  void runModeExecutesLogicAndReturnsSuccess() {
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("r1").build();
    var result = runner.run(tx("T"), ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("ok-T");
  }

  @Test
  void explainModeReturnsDefaultReportWithoutRunningExecute() {
    var executeCalled = new AtomicBoolean(false);
    var tx = Dsl.transaction("T")
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("ok-T");
            })
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.EXPLAIN).runId("r2").build();
    var result = runner.run(tx, ctx);
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    var report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("T");
    assertThat(report.mermaid()).isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
    assertThat(report.description()).isEmpty();
  }

  @Test
  void previewModeExecutesLogic() {
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW).runId("r3").build();
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
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("r4").build();
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
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("my-run").build();
    var result = runner.run(tx, ctx);
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    assertThat(((DslException) result.cause()).runId()).isEqualTo("my-run");
  }

  @Test
  void explainModeUsesExplainLogicWhenSet() {
    var executeCalled = new AtomicBoolean(false);
    var explainCalled = new AtomicBoolean(false);
    var tx = Dsl.transaction("ExplainT")
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("execute-result");
            })
            .explain(ctx -> {
              explainCalled.set(true);
              assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
              return Result.success(ExplainReport.builder().name("ExplainT")
                      .description("explain-result").mermaid("").build());
            })
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.EXPLAIN).runId("r5").build();
    var result = runner.run(tx, ctx);
    assertThat(explainCalled.get()).isTrue();
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo(ExplainReport.builder().name("ExplainT")
            .description("explain-result").mermaid("").build());
  }

  @Test
  void explainModeReturnsDefaultReportWhenExplainNotSet() {
    var executeCalled = new AtomicBoolean(false);
    var tx = Dsl.transaction("ExplainFallbackT")
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("execute-result");
            })
            .preview(ctx -> Result.success("preview-result"))
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.EXPLAIN).runId("r5").build();
    var result = runner.run(tx, ctx);
    assertThat(executeCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    var report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("ExplainFallbackT");
    assertThat(report.mermaid()).isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
    assertThat(report.description()).isEmpty();
  }

  @Test
  void previewModeUsesPreviewLogicWhenSet() {
    var tx = Dsl.transaction("PrevT")
            .execute(ctx -> Result.success("execute-result"))
            .preview(ctx -> Result.success("preview-result"))
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW).runId("r6").build();
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
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("r-reg").build();

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(compensationRegistry.hasCompensation("r-reg")).isTrue();
    compensationRegistry.compensateAll("r-reg", new RuntimeException("boom"));
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
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("r-saga").build()
            .withSaga(saga);

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
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("r-no-comp").build();

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(compensationRegistry.hasCompensation("r-no-comp")).isFalse();
  }

  @Test
  void failedTransactionIsRecordedWithFailedStatus() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-fail", repo);
    var tx = Dsl.transaction("BoomTx")
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.RUN).runId("run-fail").build()
            .withExecutionListener(listener);

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isFalse();
    var history = listener.historyInReverse();
    assertThat(history).hasSize(1);
    assertThat(history.get(0).status()).isEqualTo(TransactionExecutionStatus.FAILED);
    assertThat(history.get(0).error()).isEqualTo("boom");
  }

  @Test
  void compensationRunIsRecordedWithCompensatedStatus() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-comp", repo);
    var tx = Dsl.transaction("CompTx")
            .execute(ctx -> Result.success("ok"))
            .build();
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.COMPENSATION).runId("run-comp")
            .build()
            .withExecutionListener(listener);

    var result = runner.run(tx, ctx);

    assertThat(result.isSuccess()).isTrue();
    var history = listener.historyInReverse();
    assertThat(history).hasSize(1);
    assertThat(history.get(0).status()).isEqualTo(TransactionExecutionStatus.COMPENSATED);
  }
}
