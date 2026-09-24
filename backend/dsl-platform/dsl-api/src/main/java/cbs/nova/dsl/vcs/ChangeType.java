package cbs.nova.dsl.vcs;

/**
 * Classification of a path in a git working tree. A single path can appear in more than one JGit
 * {@code Status} set; the classifier collapses those signals to one value with precedence
 * {@code CONFLICTING > DELETED > ADDED > UNTRACKED > MODIFIED}.
 */
public enum ChangeType {
  ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING
}
