package cbs.nova.dsl.model;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * One node of an Explain call graph: a name, its own description/diagram, and links ({@code
 * children}) to the {@link ExplainReport} of every entity it calls. Pure data — merging,
 * budget-bounded truncation, and whole-graph markdown rendering live in {@link ExplainReports}.
 */
public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaid,
        @NonNull List<ExplainReport> children) {

  public ExplainReport(@NonNull String name, @NonNull String description, @NonNull String mermaid) {
    this(name, description, mermaid, List.of());
  }

  public ExplainReport {
    children = List.copyOf(children);
  }

  public static ExplainReport empty(String name) {
    return new ExplainReport(name, "", "", List.of());
  }

  public @NonNull ExplainReport withChildren(@NonNull List<ExplainReport> children) {
    return new ExplainReport(name, description, mermaid, children);
  }

  public @NonNull ExplainReport addChild(@NonNull ExplainReport child) {
    var next = new ArrayList<>(children);
    next.add(child);
    return withChildren(next);
  }
}
