package cbs.nova.dsl.explain;

import cbs.nova.dsl.model.ExplainReport;
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

  @NonNull
  String name();

  @NonNull
  String description();

  @NonNull
  String filename();

  @NonNull
  String content();

  default @NonNull ExplainResource resource() {
    return new ExplainResource(
            ExplainReport.builder()
                    .name(name())
                    .description(description())
                    .markdown(content())
                    .build(),
            filename());
  }
}
