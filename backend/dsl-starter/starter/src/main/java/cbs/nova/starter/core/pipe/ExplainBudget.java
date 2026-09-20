package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.ExplainReport;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ExplainBudget {

  private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
          .getEncoding(EncodingType.CL100K_BASE);

  private ExplainBudget() {
  }

  public static @NonNull ExplainReport apply(
          @NonNull ExplainReport report,
          int budgetChars,
          int nameMaxTokens,
          int descriptionMaxTokens,
          int mermaidMaxTokens) {
    ExplainReport clamped = clampReport(report, new HashSet<>(),
            nameMaxTokens, descriptionMaxTokens, mermaidMaxTokens);
    String markdown = clamped.markdown();
    int budget = Math.max(budgetChars, 0);
    int descriptionLimit = Math.min(clamped.description().length(), budget);
    String truncatedDescription = clamped.description().substring(0, descriptionLimit);
    int remainingBudget = Math.max(budget - truncatedDescription.length(), 0);
    int mermaidLimit = Math.min(markdown.length(), remainingBudget);
    String truncatedMermaid = markdown.substring(0, mermaidLimit);
    return new ExplainReport(
            clamped.name(),
            truncatedDescription,
            truncatedMermaid,
            clamped.children());
  }

  /**
   * Forces initialization of the statically memoized CL100K_BASE encoding. Called from an async
   * startup hook so the ~430 ms registry init cost is paid during application warmup instead of on
   * the first live explain request.
   */
  public static void warmUpEncoding() {
    ENCODING.countTokens("");
  }

  private static ExplainReport clampReport(
          ExplainReport report,
          Set<String> visited,
          int nameMaxTokens,
          int descriptionMaxTokens,
          int mermaidMaxTokens) {
    if (!visited.add(report.name())) {
      return report;
    }
    List<ExplainReport> children = report.children().stream()
            .map(child -> clampReport(child, visited, nameMaxTokens,
                    descriptionMaxTokens, mermaidMaxTokens))
            .toList();
    return new ExplainReport(
            clamp(report.name(), nameMaxTokens),
            clamp(report.description(), descriptionMaxTokens),
            clamp(report.markdown(), mermaidMaxTokens),
            children);
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
