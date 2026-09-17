package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ExplainBudgetStage implements DslPipeStage {

  // TODO: measure in CL100K_BASE Encoding is very slow, we need: memoize and some on start async
  // warmup, to speed up runtime
  private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
          .getEncoding(EncodingType.CL100K_BASE);

  private final int budgetChars;
  private final int nameMaxTokens;
  private final int descriptionMaxTokens;
  private final int mermaidMaxTokens;

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Result<?> result = next.proceed(context);
    if (!result.isSuccess()) {
      return result;
    }
    ExplainGraphReport report = (ExplainGraphReport) result.value();
    if (report == null) {
      return result;
    }
    ExplainGraphReport clamped = clampReport(report, new HashSet<>());
    String mermaid = clamped.mermaidDiagram() != null ? clamped.mermaidDiagram() : "";
    int budget = Math.max(budgetChars, 0);
    int descriptionLimit = Math.min(clamped.description().length(), budget);
    String truncatedDescription = clamped.description().substring(0, descriptionLimit);
    int remainingBudget = Math.max(budget - truncatedDescription.length(), 0);
    int mermaidLimit = Math.min(mermaid.length(), remainingBudget);
    String truncatedMermaid = mermaid.substring(0, mermaidLimit);
    var truncated = new ExplainGraphReport(
            clamped.name(),
            truncatedDescription,
            clamped.executionTrace(),
            clamped.externalCalls(),
            clamped.callCounts(),
            clamped.hasCompensation(),
            clamped.executableDescriptor(),
            clamped.dslDescriptor(),
            clamped.astTree(),
            clamped.dryRunLogs(),
            clamped.metrics(),
            clamped.errors(),
            clamped.children(),
            truncatedMermaid);
    return Result.success(truncated);
  }

  private ExplainGraphReport clampReport(ExplainGraphReport report, Set<String> visited) {
    if (!visited.add(report.name())) {
      return report;
    }
    List<ExplainGraphReport> children = report.children().stream()
            .map(child -> clampReport(child, visited))
            .toList();
    return new ExplainGraphReport(
            clamp(report.name(), nameMaxTokens),
            clamp(report.description(), descriptionMaxTokens),
            report.executionTrace(),
            report.externalCalls(),
            report.callCounts(),
            report.hasCompensation(),
            report.executableDescriptor(),
            report.dslDescriptor(),
            report.astTree(),
            report.dryRunLogs(),
            report.metrics(),
            report.errors(),
            children,
            clamp(report.mermaidDiagram(), mermaidMaxTokens));
  }

  private static @Nullable String clamp(@Nullable String text, int maxTokens) {
    if (text == null) {
      return null;
    }
    if (maxTokens <= 0) {
      return "";
    }
    if (ENCODING.countTokens(text) <= maxTokens) {
      return text;
    }
    var tokenIds = ENCODING.encode(text);
    var kept = new IntArrayList(maxTokens);
    for (int i = 0; i < maxTokens; i++) {
      kept.add(tokenIds.get(i));
    }
    return ENCODING.decode(kept);
  }
}
