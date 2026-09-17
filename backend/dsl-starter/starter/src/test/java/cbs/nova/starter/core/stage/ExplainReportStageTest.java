package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.Descriptors;
import cbs.nova.dsl.model.ExplainGraphAccumulator;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.ExplainGraphAccumulators;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainReportStageTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void alwaysReturnsSuccessRegardlessOfInnerDslResultOutcome() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.failure(new RuntimeException("boom")));
      return Result.failure(new RuntimeException("downstream"));
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.errors()).hasSize(1);
    assertThat(report.errors().get(0).message()).contains("boom");
  }

  @Test
  void descriptionIsBuiltFromDescriptorTypeWhenFound() {
    String fnName = "MyFn-" + System.nanoTime();
    GlobalManager.globalManager().registerFunction(
            Dsl.function(fnName)
                    .execute(c -> Result.success("ok"))
                    .describe(() -> Descriptors.from(fnName,
                            new ExecutableDescriptor(
                                    fnName, null, null, null, false, null, List.of())))
                    .build());

    DslPipeContext pipeContext = pipeContext(fnName, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.description()).isEqualTo("Function: " + fnName);
    assertThat(report.dslDescriptor()).isNotNull();
    assertThat(report.dslDescriptor().type()).isEqualTo(DslObject.DslType.FUNCTION);
  }

  @Test
  void hasCompensationIsCapturedAtBuildTimeForProcessWithCompensation() {
    String processName = "CompProcess-" + System.nanoTime();
    GlobalManager.globalManager().registerProcess(
            Dsl.process(processName)
                    .execute(ctx -> Result.success("ok"))
                    .compensation((ctx, history) -> ctx.log("rolled back"))
                    .build());

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(
            pipeContext(processName, ExecutionMode.PREVIEW), c -> Result.success("downstream"));

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.hasCompensation()).isTrue();
    assertThat(report.mermaidDiagram()).contains("Compensate[Compensate]");
  }

  @Test
  void hasCompensationIsFalseForProcessAndHelperWithoutCompensation() {
    String processName = "PlainProcess-" + System.nanoTime();
    GlobalManager.globalManager().registerProcess(
            Dsl.process(processName).execute(ctx -> Result.success("ok")).build());
    String helperName = "echo-helper-" + System.nanoTime();
    GlobalManager.globalManager().registerHelper(helperName, new EchoHelper());

    Result<?> processResult = new ExplainReportStage(new ExplainDiagramRenderer()).execute(
            pipeContext(processName, ExecutionMode.PREVIEW), c -> Result.success("downstream"));
    Result<?> helperResult = new ExplainReportStage(new ExplainDiagramRenderer()).execute(
            pipeContext(helperName, ExecutionMode.PREVIEW), c -> Result.success("downstream"));

    assertThat(((ExplainGraphReport) processResult.value()).hasCompensation()).isFalse();
    assertThat(((ExplainGraphReport) helperResult.value()).hasCompensation()).isFalse();
  }

  @Test
  void reportIsBuiltWithEmptyChildren() {
    String processName = "SoloProcess-" + System.nanoTime();
    GlobalManager.globalManager().registerProcess(
            Dsl.process(processName).execute(ctx -> Result.success("ok")).build());

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(
            pipeContext(processName, ExecutionMode.PREVIEW), c -> Result.success("downstream"));

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void descriptionFallsBackToHelperPrefixWhenHelperIsRegistered() {
    String helperName = "echo-helper-" + System.nanoTime();
    GlobalManager.globalManager().registerHelper(helperName, new EchoHelper());

    DslPipeContext pipeContext = pipeContext(helperName, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.description()).isEqualTo("Helper: " + helperName);
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.executableDescriptor()).isNotNull();
  }

  @Test
  void descriptionFallsBackToEntityPrefixWhenNothingRegistered() {
    String orphan = "orphan-" + System.nanoTime();
    DslPipeContext pipeContext = pipeContext(orphan, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.description()).isEqualTo("Entity: " + orphan);
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.executableDescriptor()).isNull();
  }

  @Test
  void errorsAreEmptyWhenDslResultSucceeded() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.success("ok"));
      return Result.success("downstream");
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void errorsAreEmptyWhenDslResultAttributeMissing() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void errorsPopulatedFromFailedDslResult() {
    DslPipeContext pipeContext = pipeContext("Ping", ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.failure(new RuntimeException("kaboom")));
      return Result.success("downstream");
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.errors()).hasSize(1);
    ErrorResponse detail = report.errors().get(0);
    assertThat(detail.code()).isEqualTo(PreviewErrorCode.UNKNOWN_ERROR.name());
    assertThat(detail.message()).isEqualTo("kaboom");
  }

  @Test
  void externalCallsAndCallCountsDefaultToEmptyWhenAccumulatorEmpty() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.externalCalls()).isEmpty();
    assertThat(report.callCounts()).isEmpty();
  }

  @Test
  void externalCallsAndCallCountsArePopulatedFromAccumulator() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    accumulatorOf(pipeContext)
            .externalCalls(List.of(
                    Map.of("type", "database", "target", "jdbc:db", "operation", "select",
                            "timestamp", 0L, "metadata", Map.of()),
                    Map.of("type", "http", "target", "http://x", "operation", "GET",
                            "timestamp", 0L, "metadata", Map.of())))
            .callCounts(Map.of("database", 1, "http", 1));
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.externalCalls()).hasSize(2);
    assertThat(report.callCounts())
            .containsEntry("database", 1)
            .containsEntry("http", 1);
  }

  @Test
  void reportAggregatesAccumulatorContributionsFromAllStages() {
    DslPipeContext pipeContext = pipeContext("Ping", ExecutionMode.PREVIEW);
    accumulatorOf(pipeContext)
            .executionTrace(List.of("step-1", "step-2"))
            .astTree(CallNode.leaf("Ping", cbs.nova.dsl.CallKind.PROCESS, null, "ok", true))
            .dryRunLogs(List.of(Map.of("level", "INFO", "message", "hello")))
            .metrics(new cbs.nova.dsl.PreviewMetricsSnapshot(1, 0,
                    Map.of(cbs.nova.dsl.CallKind.PROCESS, 1), Map.of()));
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.executionTrace()).containsExactly("step-1", "step-2");
    assertThat(report.dryRunLogs()).hasSize(1);
    assertThat(report.metrics()).isNotNull();
    assertThat(report.astTree()).isNotNull();
    assertThat(report.name()).isEqualTo("Ping");
  }

  @Test
  void missingAccumulatorFailsFast() {
    DslPipeContext pipeContext = DslPipeContext.of("Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW, "run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    assertThatThrownBy(() -> new ExplainReportStage(new ExplainDiagramRenderer())
            .execute(pipeContext, next))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ExplainGraphAccumulator");
  }

  @Test
  void attributeDefaultsAppliedWhenExecutionTraceAndDryRunLogsMissing() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainGraphReport report = (ExplainGraphReport) result.value();
    assertThat(report.executionTrace()).isEmpty();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.astTree()).isNull();
    assertThat(report.metrics()).isNull();
  }

  private DslPipeContext pipeContext(String name, ExecutionMode mode) {
    Context<?> ctx = SimpleContext.builder("body").mode(mode).runId("run-1").build()
            .withMetadata(Constants.EXPLAIN_GRAPH_ACCUMULATOR_KEY,
                    new ExplainGraphAccumulator());
    return DslPipeContext.of(name, ctx, mode, "run-1");
  }

  private ExplainGraphAccumulator accumulatorOf(DslPipeContext pipeContext) {
    return ExplainGraphAccumulators.resolve(pipeContext).orElseThrow();
  }

  private static final class EchoHelper implements Executable<Object, Object> {

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("echo");
    }
  }
}
