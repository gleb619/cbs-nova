package cbs.nova.dsl.vcs;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared, JGit-free classification of git status sets. Collapses staged and unstaged variants into
 * one {@link ChangeType} per path with precedence
 * {@code CONFLICTING > DELETED > ADDED > UNTRACKED > MODIFIED}.
 */
public final class GitChangeClassifier {

  private GitChangeClassifier() {
  }

  /**
   * Classify the supplied JGit status sets into a path→{@link ChangeType} map. Later writes win, so
   * {@code conflicting} overrides {@code removed}/{@code missing}, which override {@code added},
   * which overrides {@code untracked}, which overrides {@code changed}/ {@code modified}.
   *
   * @param added
   *          paths staged as added
   * @param changed
   *          paths staged as modified
   * @param modified
   *          paths unstaged in the working tree
   * @param untracked
   *          paths not tracked by git
   * @param removed
   *          paths staged as removed
   * @param missing
   *          paths deleted in the working tree but not staged
   * @param conflicting
   *          paths with unmerged content
   * @return an unmodifiable-equivalent map from path to collapsed change type
   */
  public static Map<String, ChangeType> classify(
          Set<String> added,
          Set<String> changed,
          Set<String> modified,
          Set<String> untracked,
          Set<String> removed,
          Set<String> missing,
          Set<String> conflicting) {
    Map<String, ChangeType> changes = new HashMap<>();
    for (String p : nullSafe(changed)) {
      changes.put(p, ChangeType.MODIFIED);
    }
    for (String p : nullSafe(modified)) {
      changes.put(p, ChangeType.MODIFIED);
    }
    for (String p : nullSafe(untracked)) {
      changes.put(p, ChangeType.UNTRACKED);
    }
    for (String p : nullSafe(added)) {
      changes.put(p, ChangeType.ADDED);
    }
    for (String p : nullSafe(removed)) {
      changes.put(p, ChangeType.DELETED);
    }
    for (String p : nullSafe(missing)) {
      changes.put(p, ChangeType.DELETED);
    }
    for (String p : nullSafe(conflicting)) {
      changes.put(p, ChangeType.CONFLICTING);
    }
    return changes;
  }

  private static Set<String> nullSafe(Set<String> set) {
    return set == null ? Set.of() : set;
  }
}
