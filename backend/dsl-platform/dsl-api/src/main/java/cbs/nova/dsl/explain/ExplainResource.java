package cbs.nova.dsl.explain;

import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;

/**
 * Immutable view of an explain resource: the underlying {@link ExplainReport} plus the on-disk
 * {@code filename} (which {@link ExplainReport} does not carry).
 *
 * <p>
 * {@code name()}, {@code description()}, {@code content()} delegate to the {@link ExplainReport}
 * equivalents for callers that only needed the scalar fields.
 */
public record ExplainResource(@NonNull ExplainReport report, @NonNull String filename) {

  public @NonNull String name() {
    return report.name();
  }

  public @NonNull String description() {
    return report.description();
  }

  public @NonNull String content() {
    return report.markdown();
  }

  public static @NonNull ExplainResource empty(@NonNull String name) {
    return new ExplainResource(ExplainReport.builder().name(name).build(), "");
  }
}