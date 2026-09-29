package cbs.nova.starter.helper.model;

import java.util.List;

/**
 * Output for the built-in {@code jsonDiff} helper.
 *
 * <p>
 * {@code changes} is the flat list of differences discovered by walking {@code left} and
 * {@code right} in parallel; each entry carries a JSONPointer-ish {@code path} (e.g.
 * {@code $.a.b[0].c}), an {@code op} of {@code "added"}, {@code "removed"}, or {@code "changed"},
 * plus the corresponding JSON-encoded {@code left} and {@code right} values (one of which is
 * {@code null} for add/remove; both populated for change). {@code identical} is {@code true}
 * exactly when {@code changes} is empty.
 *
 * <p>
 * Path notation mirrors the {@code schemaValidate} {@code $.field[0].nested} convention: a leading
 * {@code $}, object keys joined with {@code .}, and array indices in {@code [n]} segments.
 */
public record JsonDiffOut(List<Change> changes, boolean identical) {

  /**
   * Single per-path diff entry.
   *
   * <p>
   * {@code op} is one of {@code "added"}, {@code "removed"}, {@code "changed"}. For {@code "added"}
   * only {@code right} is populated; for {@code "removed"} only {@code left} is populated; for
   * {@code "changed"} both are populated. Values are JSON-encoded via Jackson.
   */
  public record Change(String path, String op, String left, String right) {
  }
}
