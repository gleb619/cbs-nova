package cbs.nova.dsl.builder.config;

/**
 * How dsl-builder resolves the directory that backs DSL file I/O (docs/backend/drafts.md §1).
 *
 * <p>
 * {@link #SHARED} — the builder works directly on the operator-configured source directory
 * ({@code cbs.dsl.builder.source-dir}, falling back to {@code cbs.dsl.builder.workspace-dir}). File
 * reads and writes hit exactly the files the user edits in the IDE; git operations run in that
 * directory. This is the dev/local mode and the default.
 *
 * <p>
 * {@link #WORKTREE} — the builder manages its own git worktree at
 * {@code cbs.dsl.builder.workspace-dir}, provisioned from {@code cbs.dsl.builder.git.repo-url} /
 * {@code branch} / {@code sub-path} when empty. The starter's {@code cbs.dsl.source-dir} must NOT
 * be expected to match; metadata reports the builder's actual workspace path.
 */
public enum WorkspaceMode {

  SHARED, WORKTREE;

  /** Lenient parse for configuration values; anything unknown falls back to {@link #SHARED}. */
  public static WorkspaceMode from(String value) {
    if (value == null || value.isBlank()) {
      return SHARED;
    }
    for (WorkspaceMode mode : values()) {
      if (mode.name().equalsIgnoreCase(value.trim())) {
        return mode;
      }
    }
    return SHARED;
  }
}
