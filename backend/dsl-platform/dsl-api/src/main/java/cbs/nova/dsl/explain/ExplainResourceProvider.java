package cbs.nova.dsl.explain;

import org.jspecify.annotations.NonNull;

/**
 * SPI contract for an explain resource provider. The DSL engine discovers implementations via
 * {@link java.util.ServiceLoader}, so user code can plug in additional sources (HTTP, database, S3,
 * etc.) without recompiling the platform.
 *
 * <p>
 * Generated providers expose YAML-frontmatter metadata as type-safe constants and load the markdown
 * body lazily through a configured {@link ExplainResourceResolver}.
 */
public interface ExplainResourceProvider {

  /** Logical name from frontmatter; falls back to the filename stem when absent. */
  @NonNull
  String name();

  /** Description from frontmatter; empty string when no frontmatter. */
  @NonNull
  String description();

  /** Filename on disk, e.g. {@code batch-processing.md}. */
  @NonNull
  String filename();

  /** Markdown body with frontmatter stripped. */
  @NonNull
  String content();

  /** Snapshot view of all four fields; useful for callers that want a value object. */
  default @NonNull ExplainResource resource() {
    return new ExplainResource(name(), description(), filename(), content());
  }
}
