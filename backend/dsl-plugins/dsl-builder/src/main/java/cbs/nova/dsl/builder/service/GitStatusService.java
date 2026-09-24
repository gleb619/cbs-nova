package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GitStatusService {

  private final DslBuilderProperties properties;
  private final Clock clock;
  private final ConcurrentHashMap<Path, Snapshot> cache = new ConcurrentHashMap<>();

  @Autowired
  public GitStatusService(DslBuilderProperties properties) {
    this(properties, Clock.systemUTC());
  }

  // Test seam: deterministic clock for TTL cache assertions. Behavior-preserving.
  GitStatusService(DslBuilderProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public Optional<RepoStatus> status(Path candidateDir) {
    if (!gitEnabled() || candidateDir == null) {
      return Optional.empty();
    }
    Path root = repositoryRoot(candidateDir);
    Snapshot cached = cache.get(root);
    if (cached != null && !clock.instant().isAfter(cached.expiresAt())) {
      return Optional.of(cached.repoStatus());
    }
    try {
      RepoStatus repoStatus = loadStatus(root);
      cache.put(root, new Snapshot(repoStatus, clock.instant().plus(ttl())));
      return Optional.of(repoStatus);
    } catch (Exception e) {
      log.warn("[DSL git] failed to read status for {}: {}", root, e.getMessage());
      return Optional.empty();
    }
  }

  private RepoStatus loadStatus(Path root) throws Exception {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(root.toFile());
    if (builder.getGitDir() == null) {
      throw new IOException("no git repository found under " + root);
    }
    Repository repository = builder.build();
    try (Git git = new Git(repository)) {
      Status status = git.status().call();
      Map<String, ChangeType> changes = classify(status);
      return RepoStatus.of(
              repository.getWorkTree().toPath().toAbsolutePath().normalize(),
              changes);
    }
  }

  private boolean gitEnabled() {
    return properties.git() != null && properties.git().enabled();
  }

  private Path repositoryRoot(Path candidateDir) {
    String configured = properties.git() != null
            ? properties.git().repositoryDir()
            : null;
    return configured != null && !configured.isBlank()
            ? Path.of(configured).toAbsolutePath().normalize()
            : candidateDir.toAbsolutePath().normalize();
  }

  private Duration ttl() {
    int seconds = properties.git() != null
            ? properties.git().statusCacheTtlSeconds()
            : 5;
    return Duration.ofSeconds(Math.max(0, seconds));
  }

  /**
   * Per-path classification of the JGit {@link Status} set. Precedence (highest first) is
   * CONFLICTING, DELETED, ADDED, MODIFIED — staged and unstaged variants collapse to one badge.
   */
  public enum ChangeType {
    ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING
  }

  /**
   * Snapshot of a repository: the work tree path, the union of all changed paths
   * ({@code dirtyPaths}), and the typed classification per path ({@code changes}).
   * Backward compatible: the legacy 2-arg constructor leaves {@code changes} empty.
   */
  public record RepoStatus(Path workTree, Set<String> dirtyPaths,
          Map<String, ChangeType> changes) {

    public RepoStatus {
      changes = changes == null ? Map.of() : Map.copyOf(changes);
      dirtyPaths = dirtyPaths == null ? changes.keySet() : Set.copyOf(dirtyPaths);
    }

    /** Legacy constructor for callers/tests that only carry the dirty set. */
    public RepoStatus(Path workTree, Set<String> dirtyPaths) {
      this(workTree, dirtyPaths, null);
    }

    /** Build a {@code RepoStatus} whose {@code dirtyPaths} is the key set of {@code changes}. */
    public static RepoStatus of(Path workTree, Map<String, ChangeType> changes) {
      return new RepoStatus(workTree, null, changes);
    }

    /** Return the change type for {@code path}, if any. */
    public Optional<ChangeType> changeOf(String path) {
      ChangeType type = changes.get(path);
      return type == null ? Optional.empty() : Optional.of(type);
    }
  }

  /**
   * Classify a JGit {@link Status} into a path→{@link ChangeType} map. Later writes win so
   * precedence collapses to {@code CONFLICTING > DELETED > ADDED > UNTRACKED > MODIFIED}.
   */
  static Map<String, ChangeType> classify(Status status) {
    Map<String, ChangeType> changes = new HashMap<>();
    for (String p : status.getChanged()) {
      changes.put(p, ChangeType.MODIFIED);
    }
    for (String p : status.getModified()) {
      changes.put(p, ChangeType.MODIFIED);
    }
    for (String p : status.getUntracked()) {
      changes.put(p, ChangeType.UNTRACKED);
    }
    for (String p : status.getAdded()) {
      changes.put(p, ChangeType.ADDED);
    }
    for (String p : status.getRemoved()) {
      changes.put(p, ChangeType.DELETED);
    }
    for (String p : status.getMissing()) {
      changes.put(p, ChangeType.DELETED);
    }
    for (String p : status.getConflicting()) {
      changes.put(p, ChangeType.CONFLICTING);
    }
    return changes;
  }

  private record Snapshot(RepoStatus repoStatus, Instant expiresAt) {
  }
}
