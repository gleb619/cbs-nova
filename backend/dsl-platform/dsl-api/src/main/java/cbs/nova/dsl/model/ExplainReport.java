package cbs.nova.dsl.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;

/**
 * One node of an Explain call graph: a name, its own description/diagram, and links ({@code
 * children}) to the {@link ExplainReport} of every entity it calls. Pure data — merging,
 * budget-bounded truncation, and whole-graph markdown rendering live in {@link ExplainReports}.
 *
 * <p>
 * Field semantics:
 * <ul>
 * <li>{@code description} — short summary, typically the YAML frontmatter description.</li>
 * <li>{@code mermaid} — full markdown body of the explain resource (the long-form content, rendered
 * or diagrammed downstream).</li>
 * </ul>
 */
@Builder
public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaid,
        @NonNull List<ExplainReport> children) {

  public ExplainReport {
    description = description == null ? "" : description;
    mermaid = mermaid == null ? "" : mermaid;
    children = children == null ? List.of() : List.copyOf(children);
  }

  /** Shortcut: explain resource carrying only its long-form markdown body in {@code mermaid}. */
  public static @NonNull ExplainReport of(@NonNull String name, @NonNull String mermaid) {
    return ExplainReport.builder().name(name).mermaid(mermaid).build();
  }

  /** Empty report — no description, no body, no children. */
  public static ExplainReport empty(String name) {
    return ExplainReport.builder().name(name).build();
  }

  public @NonNull ExplainReport withChildren(@NonNull List<ExplainReport> children) {
    return ExplainReport.builder()
            .name(name)
            .description(description)
            .mermaid(mermaid)
            .children(children)
            .build();
  }

  public @NonNull ExplainReport addChild(@NonNull ExplainReport child) {
    var next = new ArrayList<>(children);
    next.add(child);
    return withChildren(next);
  }
}
