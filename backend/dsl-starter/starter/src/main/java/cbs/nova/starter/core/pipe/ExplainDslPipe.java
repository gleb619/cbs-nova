package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExplainDslPipe implements DslExecutionPipe<ExplainReport> {

  private final HierarchyDslPipe hierarchyDslPipe;
  private final CbsNovaExplainProperties explainProperties;

  @Override
  public @NonNull Result<ExplainReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    Result<HierarchyReport> hierarchyResult = hierarchyDslPipe.execute(name, hierarchyContext(ctx));
    if (!hierarchyResult.isSuccess() || hierarchyResult.value() == null) {
      return Result.failure(hierarchyResult.cause() != null
              ? hierarchyResult.cause()
              : new IllegalStateException("hierarchy produced no report for " + name));
    }
    ExplainNodeExplainer nodeExplainer = new ExplainNodeExplainer(explainProperties.budgetChars());
    ExplainReport explainRoot = ExplainMapper.fromHierarchy(hierarchyResult.value(),
            nodeExplainer, ctx);
    ExplainReport bounded = ExplainBudget.apply(
            explainRoot,
            explainProperties.budgetChars(),
            explainProperties.nameMaxTokens(),
            explainProperties.descriptionMaxTokens(),
            explainProperties.mermaidMaxTokens());
    return Result.success(bounded);
  }

  private @NonNull Context<?> hierarchyContext(@NonNull Context<?> ctx) {
    return SimpleContext.builder()
            .body(ctx.body())
            .metadata(ctx.metadata())
            .mode(ExecutionMode.HIERARCHY)
            .runId(ctx.runId())
            .build();
  }
}
