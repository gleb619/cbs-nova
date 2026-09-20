package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExplainReportStageTest {

  private final ExplainReportStage stage = new ExplainReportStage(
          new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096));

  private DslPipeContext context() {
    var ctx = SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("r1").build();
    return DslPipeContext.of("Root", ctx, ExecutionMode.EXPLAIN, "r1");
  }

  @Test
  void keepsExplainModeAndPassesBudgetDownstream() {
    AtomicReference<DslPipeContext> seen = new AtomicReference<>();
    DslPipeContext pipeContext = context();

    stage.execute(pipeContext, c -> {
      seen.set(c);
      c.setAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE,
              Result.success(ExplainReport.builder().name("Root").build()));
      return Result.success(null);
    });

    assertThat(seen.get().mode()).isEqualTo(ExecutionMode.EXPLAIN);
    assertThat(seen.get().dslContext().metadata())
            .containsEntry(Constants.EXPLAIN_BUDGET_CHARS_KEY, 4000);
  }

  @Test
  void returnsDispatchedReportKeepingChildren() {
    ExplainReport child = ExplainReport.builder().name("child").description("d").build();
    ExplainReport root = ExplainReport.builder().name("Root").description("r")
            .children(java.util.List.of(child)).build();

    Result<?> result = stage.execute(context(), c -> {
      c.setAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE, Result.success(root));
      return Result.success(null);
    });

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.children()).extracting(ExplainReport::name).containsExactly("child");
  }

  @Test
  void failsWhenDispatchFailed() {
    Result<?> result = stage.execute(context(), c -> {
      c.setAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE,
              Result.failure(new IllegalArgumentException("boom")));
      return Result.success(null);
    });

    assertThat(result.isSuccess()).isFalse();
  }
}
