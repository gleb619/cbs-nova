package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreviewReportStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void successPathPopulatesOutputAndHasEmptyErrors() {
    DslPipeContext pipeContext = pipeContext();
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.success("produced-output"));
      return Result.success("downstream");
    };

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isEqualTo("produced-output");
    assertThat(report.errors()).isEmpty();
    assertThat(report.name()).isEqualTo("Ping");
    assertThat(report.mode()).isEqualTo(ExecutionMode.PREVIEW);
  }

  @Test
  void failurePathProducesNullOutputAndOneError() {
    DslPipeContext pipeContext = pipeContext();
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.failure(new RuntimeException("kaboom")));
      return Result.failure(new RuntimeException("kaboom"));
    };

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.success()).isFalse();
    assertThat(report.output()).isNull();
    assertThat(report.errors()).hasSize(1);
    PreviewErrorDetail detail = report.errors().get(0);
    assertThat(detail.code()).isEqualTo(PreviewErrorCode.UNKNOWN_ERROR);
    assertThat(detail.message()).isEqualTo("kaboom");
  }

  @Test
  void missingDslResultAttributeYieldsNullOutputAndEmptyErrors() {
    DslPipeContext pipeContext = pipeContext();
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.success()).isFalse();
    assertThat(report.output()).isNull();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void attributeDefaultsAppliedWhenExecutionTraceAndDryRunLogsMissing() {
    DslPipeContext pipeContext = pipeContext();
    pipeContext.setAttribute("astTree",
        CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.executionTrace()).isEmpty();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.metrics()).isNull();
    assertThat(report.astTree()).isNotNull();
    assertThat(report.astTree().name()).isEqualTo("root");
  }

  @Test
  void attributeValuesFlowThroughWhenProvided() {
    DslPipeContext pipeContext = pipeContext();
    List<String> trace = List.of("step-1", "step-2");
    List<Map<String, Object>> logs = List.of(Map.of("message", "log-1"));
    PreviewMetricsSnapshot metrics = new PreviewMetricsSnapshot(10L, 256L,
        Map.of(CallKind.PROCESS, 1), Map.of("http", 2));
    pipeContext.setAttribute("executionTrace", trace);
    pipeContext.setAttribute("dryRunLogs", logs);
    pipeContext.setAttribute("metrics", metrics);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.success("ok"));
      return Result.success("downstream");
    };

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.executionTrace()).containsExactly("step-1", "step-2");
    assertThat(report.dryRunLogs()).hasSize(1);
    assertThat(report.metrics()).isSameAs(metrics);
  }

  @Test
  void externalCallsAndCallCountsDefaultToEmptyWhenAttributeMissing() {
    DslPipeContext pipeContext = pipeContext();
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.externalCalls()).isEmpty();
    assertThat(report.callCounts()).isEmpty();
  }

  @Test
  void externalCallsAndCallCountsPopulatedFromAttribute() {
    DslPipeContext pipeContext = pipeContext();
    List<ExternalCall> calls = List.of(
        new ExternalCall("database", "jdbc:db", "select", 0L, Map.of()),
        new ExternalCall("http", "http://x", "GET", 0L, Map.of())
    );
    pipeContext.setAttribute("externalCalls", calls);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    PreviewReport report = (PreviewReport) result.value();
    assertThat(report.externalCalls()).hasSize(2);
    assertThat(report.callCounts())
        .containsEntry("database", 1)
        .containsEntry("http", 1);
  }

  @Test
  void stageAlwaysReturnsResultSuccess() {
    DslPipeContext pipeContext = pipeContext();
    DslPipeStage.Next next = c -> Result.failure(new RuntimeException("downstream"));

    Result<?> result = new PreviewReportStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(PreviewReport.class);
  }

  private DslPipeContext pipeContext() {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    return new DslPipeContext("Ping", ctx, ExecutionMode.PREVIEW, "run-1");
  }
}
