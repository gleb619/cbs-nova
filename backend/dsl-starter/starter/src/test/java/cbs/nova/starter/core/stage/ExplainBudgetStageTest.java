package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.model.ExplainTraceReport;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ExplainBudgetStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private DslPipeContext pipeContext() {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.EXPLAIN, "run-1");
    return DslPipeContext.of("Test", ctx, ExecutionMode.EXPLAIN, "run-1");
  }

  private ExplainTraceReport reportWith(String description, String mermaid) {
    return new ExplainTraceReport(
            "Test",
            description,
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            mermaid);
  }

  @Test
  void leavesReportUnchangedWhenWithinBudget() {
    var stage = new ExplainBudgetStage(100);
    var report = reportWith("short description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.mermaidDiagram()).isEqualTo("graph TD; A-->B");
  }

  @Test
  void truncatesDescriptionFirstThenDiagram() {
    var stage = new ExplainBudgetStage(20);
    var report = reportWith("this is a long description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.description()).isEqualTo("this is a long descr");
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void returnsEmptyDiagramWhenBudgetExhaustedByDescription() {
    var stage = new ExplainBudgetStage(10);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.description()).isEqualTo("descriptio");
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void handlesZeroBudget() {
    var stage = new ExplainBudgetStage(0);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void handlesNegativeBudgetAsZero() {
    var stage = new ExplainBudgetStage(-5);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void preservesAllOtherReportFields() {
    var stage = new ExplainBudgetStage(1000);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainTraceReport bounded = (ExplainTraceReport) result.value();
    assertThat(bounded.name()).isEqualTo("Test");
    assertThat(bounded.executionTrace()).isEmpty();
    assertThat(bounded.externalCalls()).isEmpty();
    assertThat(bounded.callCounts()).isEmpty();
    assertThat(bounded.dryRunLogs()).isEmpty();
    assertThat(bounded.errors()).isEmpty();
  }

  @Test
  void propagatesFailureWithoutTruncation() {
    var stage = new ExplainBudgetStage(100);
    DslPipeStage.Next next = c -> Result.failure(new RuntimeException("boom"));

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("boom");
  }
}
