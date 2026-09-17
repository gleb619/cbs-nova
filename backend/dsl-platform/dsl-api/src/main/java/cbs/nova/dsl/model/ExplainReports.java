package cbs.nova.dsl.model;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Graph operations over {@link ExplainReport}: single-node merge/truncate, and whole-graph markdown
 * rendering. The budget for a traversal can't be known until the graph is walked — a helper reused
 * by several callers should only count once, a cycle must terminate, and the last node that fits
 * must end exactly on a node boundary — so truncation is a property of the walk
 * ({@link #toMarkdown(ExplainReport, int)}), not of any one report's constructor.
 */
// TODO: move to a `util` package
@Deprecated
public final class ExplainReports {

  private ExplainReports() {
  }

  public static @NonNull ExplainReport merge(@NonNull ExplainReport left,
          @NonNull ExplainReport right) {
    return new ExplainReport(
            left.name(),
            joinMarkdown(left.description(), right.description()),
            joinMermaid(left.mermaid(), right.mermaid()),
            mergeChildren(left.children(), right.children()));
  }

  public static @NonNull String toMarkdown(@NonNull ExplainReport root, int budgetChars) {
    List<ExplainReport> visited = GraphWalk.breadthFirst(root, ExplainReport::children,
            ExplainReport::name);

    int budget = Math.max(budgetChars, 0);
    var sections = new ArrayList<String>();
    int used = 0;
    int omitted = 0;
    for (var node : visited) {
      if (omitted > 0) {
        omitted++;
        continue;
      }
      String section = section(node);
      int cost = section.length() + (sections.isEmpty() ? 0 : 2);
      if (used + cost > budget) {
        omitted = 1;
        continue;
      }
      sections.add(section);
      used += cost;
    }

    String doc = String.join("\n\n", sections);
    return omitted > 0
            ? doc + (doc.isEmpty() ? "" : "\n\n") + "... " + omitted
                    + " more nodes omitted, budget exhausted"
            : doc;
  }

  private static @NonNull String section(@NonNull ExplainReport node) {
    return node.mermaid().isEmpty()
            ? "## " + node.name() + "\n\n" + node.description()
            : "## " + node.name() + "\n\n" + node.description() + "\n\n```mermaid\n"
                    + node.mermaid() + "\n```";
  }

  private static @NonNull List<ExplainReport> mergeChildren(
          @NonNull List<ExplainReport> first, @NonNull List<ExplainReport> second) {
    var merged = new LinkedHashMap<String, ExplainReport>();
    for (var child : first) {
      merged.put(child.name(), child);
    }
    for (var child : second) {
      merged.merge(child.name(), child, ExplainReports::merge);
    }
    return List.copyOf(merged.values());
  }

  private static @NonNull String joinMarkdown(@NonNull String first, @NonNull String second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty() || EMPTY_MARKDOWN.equals(second)) {
      return first;
    }
    if (EMPTY_MARKDOWN.equals(first)) {
      return second;
    }
    return first + "\n\n" + second;
  }

  private static @NonNull String joinMermaid(@NonNull String first, @NonNull String second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty()) {
      return first;
    }
    return first + "\n" + second;
  }
}
