package cbs.nova.dsl.vcs;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot of a git repository: the work tree path, the union of all changed paths
 * ({@code dirtyPaths}), and the typed classification per path ({@code changes}). Backward
 * compatible: the legacy 2-arg constructor leaves {@code changes} empty, and a payload without
 * {@code changes} still parses with an empty map.
 */
public record RepoStatus(Path workTree, Set<String> dirtyPaths,
        Map<String, ChangeType> changes) {

  public RepoStatus {
    changes = changes == null ? Map.of() : Map.copyOf(changes);
    dirtyPaths = dirtyPaths == null ? changes.keySet() : Set.copyOf(dirtyPaths);
  }

  /** Build a {@code RepoStatus} whose {@code dirtyPaths} is the key set of {@code changes}. */
  public static RepoStatus of(Path workTree, Map<String, ChangeType> changes) {
    return new RepoStatus(workTree, null, changes);
  }

  /** Build a {@code RepoStatus} from a known dirty set and no typed changes. */
  public static RepoStatus fromDirtyPaths(Path workTree, Set<String> dirtyPaths) {
    return new RepoStatus(workTree, dirtyPaths, null);
  }

  /** Return the change type for {@code path}, if any. */
  public Optional<ChangeType> changeOf(String path) {
    return Optional.ofNullable(changes.get(path));
  }
}
