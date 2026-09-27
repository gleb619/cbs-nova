package cbs.nova.dsl.vcs;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot of a git repository: the work tree path, the union of all changed paths
 * ({@code dirtyPaths}), the typed classification per path ({@code changes}), and the on-disk size
 * in bytes per changed path that still exists as a file ({@code sizes}). Backward compatible: the
 * legacy constructors leave {@code sizes} empty, and a payload without {@code sizes} still parses
 * with an empty map.
 */
public record RepoStatus(Path workTree, Set<String> dirtyPaths,
        Map<String, ChangeType> changes, Map<String, Long> sizes) {

  public RepoStatus {
    changes = changes == null ? Map.of() : Map.copyOf(changes);
    dirtyPaths = dirtyPaths == null ? changes.keySet() : Set.copyOf(dirtyPaths);
    sizes = sizes == null ? Map.of() : Map.copyOf(sizes);
  }

  /** Build a {@code RepoStatus} without size information. */
  public RepoStatus(Path workTree, Set<String> dirtyPaths, Map<String, ChangeType> changes) {
    this(workTree, dirtyPaths, changes, null);
  }

  /** Build a {@code RepoStatus} whose {@code dirtyPaths} is the key set of {@code changes}. */
  public static RepoStatus of(Path workTree, Map<String, ChangeType> changes) {
    return new RepoStatus(workTree, null, changes);
  }

  /**
   * Build a {@code RepoStatus} with sizes whose {@code dirtyPaths} derives from {@code changes}.
   */
  public static RepoStatus of(Path workTree, Map<String, ChangeType> changes,
          Map<String, Long> sizes) {
    return new RepoStatus(workTree, null, changes, sizes);
  }

  /** Build a {@code RepoStatus} from a known dirty set and no typed changes. */
  public static RepoStatus fromDirtyPaths(Path workTree, Set<String> dirtyPaths) {
    return new RepoStatus(workTree, dirtyPaths, null);
  }

  /** Return the change type for {@code path}, if any. */
  public Optional<ChangeType> changeOf(String path) {
    return Optional.ofNullable(changes.get(path));
  }

  /** Return the on-disk size in bytes for {@code path}, if the path is changed and exists. */
  public Optional<Long> sizeOf(String path) {
    return Optional.ofNullable(sizes.get(path));
  }
}
