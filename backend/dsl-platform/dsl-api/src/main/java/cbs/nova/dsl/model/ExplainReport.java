package cbs.nova.dsl.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;

/**
 * One node of an Explain call graph: a name, its own description/diagram, and links ({@code
 * children}) to the {@link ExplainReport} of every entity it calls. Pure data — merging,
 * budget-bounded truncation, and whole-graph markdown rendering live in {@link cbs.nova.dsl.utils.ExplainReports}.
 *
 * <p>
 * Field semantics:
 * <ul>
 * <li>{@code description} — short summary, typically the YAML frontmatter description.</li>
 * <li>{@code markdown} — full markdown body of the explain resource (the long-form content,
 * rendered or diagrammed downstream).</li>
 * </ul>
 */
@Builder
public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String markdown,
        @NonNull List<ExplainReport> children) {

  public ExplainReport {
    description = description == null ? "" : description;
    markdown = markdown == null ? "" : markdown;
    children = children == null ? List.of() : List.copyOf(children);
  }

  public static @NonNull ExplainReport of(@NonNull String markdown) {
    return ExplainReport.builder().markdown(markdown).build();
  }

  public @NonNull ExplainReport withInfo(@NonNull String name, @NonNull String description) {
    return ExplainReport.builder()
            .name(name)
            .description(description)
            .markdown(markdown())
            .children(new ArrayList<>(children()))
            .build();
  }

  // TODO: add usage in source code, not only in test ones
  @Deprecated(forRemoval = true)
  public @NonNull ExplainReport withChildren(@NonNull List<ExplainReport> children) {
    return ExplainReport.builder()
            .name(name)
            .description(description)
            .markdown(markdown)
            .children(children)
            .build();
  }

  // TODO: add usage in source code, not only in test ones
  @Deprecated(forRemoval = true)
  public @NonNull ExplainReport addChild(@NonNull ExplainReport child) {
    var next = new ArrayList<>(children);
    next.add(child);
    return withChildren(next);
  }
}
