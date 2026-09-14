package cbs.nova.dsl.explain;

import org.jspecify.annotations.NonNull;

/**
 * Immutable view of an explain resource: a logical {@code name}, the {@code description} from its
 * YAML frontmatter, the on-disk {@code filename}, and the {@code content} body with frontmatter
 * stripped.
 */
// TODO: replace `name`,`description`,`content` to a ExplainReport field
@Deprecated
public record ExplainResource(
        @NonNull String name,
        @NonNull String description,
        @NonNull String filename,
        @NonNull String content) {

  public static @NonNull ExplainResource empty(@NonNull String name) {
    return new ExplainResource(name, "", "", "");
  }
}
