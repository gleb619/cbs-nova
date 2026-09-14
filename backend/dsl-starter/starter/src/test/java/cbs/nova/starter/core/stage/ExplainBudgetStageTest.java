package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplainBudgetStageTest {

  private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
          .getEncoding(EncodingType.CL100K_BASE);

  private final ContextFactory contextFactory = new ContextFactory();

  private DslPipeContext pipeContext() {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.EXPLAIN, "run-1");
    return DslPipeContext.of("Test", ctx, ExecutionMode.EXPLAIN, "run-1");
  }

  private ExplainGraphReport reportWith(String description, String mermaid) {
    return reportWith("Test", description, mermaid, List.of());
  }

  private ExplainGraphReport reportWith(String name, String description, String mermaid) {
    return reportWith(name, description, mermaid, List.of());
  }

  private ExplainGraphReport reportWith(String name, String description, String mermaid,
          List<ExplainGraphReport> children) {
    return new ExplainGraphReport(
            name,
            description,
            List.of(),
            List.of(),
            Map.of(),
            false,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            children,
            mermaid);
  }

  @Test
  void leavesReportUnchangedWhenWithinBudget() {
    var stage = new ExplainBudgetStage(100, 128, 256, 4096);
    var report = reportWith("short description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.mermaidDiagram()).isEqualTo("graph TD; A-->B");
  }

  @Test
  void truncatesDescriptionFirstThenDiagram() {
    var stage = new ExplainBudgetStage(20, 128, 256, 4096);
    var report = reportWith("this is a long description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEqualTo("this is a long descr");
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void returnsEmptyDiagramWhenBudgetExhaustedByDescription() {
    var stage = new ExplainBudgetStage(10, 128, 256, 4096);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEqualTo("descriptio");
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void handlesZeroBudget() {
    var stage = new ExplainBudgetStage(0, 128, 256, 4096);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void handlesNegativeBudgetAsZero() {
    var stage = new ExplainBudgetStage(-5, 128, 256, 4096);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void preservesAllOtherReportFields() {
    var stage = new ExplainBudgetStage(1000, 128, 256, 4096);
    var report = reportWith("description", "mermaidDiagram");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.name()).isEqualTo("Test");
    assertThat(bounded.executionTrace()).isEmpty();
    assertThat(bounded.externalCalls()).isEmpty();
    assertThat(bounded.callCounts()).isEmpty();
    assertThat(bounded.hasCompensation()).isFalse();
    assertThat(bounded.dryRunLogs()).isEmpty();
    assertThat(bounded.errors()).isEmpty();
    assertThat(bounded.children()).isEmpty();
  }

  @Test
  void clampsOverLimitFieldsToConfiguredTokenCaps() {
    var stage = new ExplainBudgetStage(100_000, 5, 10, 8);
    var report = reportWith(
            "alpha beta gamma delta epsilon zeta eta theta",
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen",
            "graph TD; alpha --> beta; gamma --> delta; epsilon --> zeta");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(ENCODING.countTokens(bounded.name())).isLessThanOrEqualTo(5);
    assertThat(ENCODING.countTokens(bounded.description())).isLessThanOrEqualTo(10);
    assertThat(ENCODING.countTokens(bounded.mermaidDiagram())).isLessThanOrEqualTo(8);
    assertThat(bounded.name()).isNotEqualTo(report.name());
    assertThat(bounded.description()).isNotEqualTo(report.description());
    assertThat(bounded.mermaidDiagram()).isNotEqualTo(report.mermaidDiagram());
  }

  @Test
  void leavesUnderLimitFieldsUnchangedWithTokenCaps() {
    var stage = new ExplainBudgetStage(100_000, 128, 256, 4096);
    var report = reportWith("short name", "short description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.name()).isEqualTo("short name");
    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.mermaidDiagram()).isEqualTo("graph TD; A-->B");
  }

  @Test
  void zeroTokenCapsClampFieldsToEmpty() {
    var stage = new ExplainBudgetStage(100_000, 0, 0, 0);
    var report = reportWith("name", "description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.name()).isEmpty();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void negativeTokenCapsClampFieldsToEmpty() {
    var stage = new ExplainBudgetStage(100_000, -3, -1, -7);
    var report = reportWith("name", "description", "graph TD; A-->B");
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.name()).isEmpty();
    assertThat(bounded.description()).isEmpty();
    assertThat(bounded.mermaidDiagram()).isEmpty();
  }

  @Test
  void clampsChildNodeFieldsDuringSameTraversal() {
    var child = reportWith("Child",
            "alpha beta gamma delta epsilon zeta eta theta iota kappa",
            "graph TD; one --> two",
            List.of());
    var report = reportWith("Test", "short description", "graph TD; A-->B", List.of(child));
    var stage = new ExplainBudgetStage(100_000, 128, 5, 4096);
    DslPipeStage.Next next = c -> Result.success(report);

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isTrue();
    ExplainGraphReport bounded = (ExplainGraphReport) result.value();
    assertThat(bounded.description()).isEqualTo("short description");
    assertThat(bounded.children()).hasSize(1);
    ExplainGraphReport clampedChild = bounded.children().get(0);
    assertThat(ENCODING.countTokens(clampedChild.description())).isLessThanOrEqualTo(5);
    assertThat(clampedChild.description()).isNotEqualTo(child.description());
    assertThat(clampedChild.mermaidDiagram()).isEqualTo("graph TD; one --> two");
  }

  @Test
  void propagatesFailureWithoutTruncation() {
    var stage = new ExplainBudgetStage(100, 128, 256, 4096);
    DslPipeStage.Next next = c -> Result.failure(new RuntimeException("boom"));

    Result<?> result = stage.execute(pipeContext(), next);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("boom");
  }
}
