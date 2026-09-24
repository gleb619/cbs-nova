package cbs.nova.starter.service;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslGitStatusResolver {

  private final DslProperties dslProperties;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ConcurrentHashMap<Path, Snapshot> cache = new ConcurrentHashMap<>();
  private Clock clock = Clock.systemUTC();

  void setClock(Clock clock) {
    this.clock = clock;
  }

  public Optional<RepoStatus> status(Path candidateDir) {
    var builder = builderClient();
    if (builder != null) {
      return builder.vcsStatus();
    }
    if (!gitEnabled()) {
      return Optional.empty();
    }
    Path root = repositoryRoot(candidateDir);
    Snapshot cached = cache.get(root);
    if (cached != null && !cached.expired()) {
      return Optional.of(cached.repoStatus);
    }
    try {
      RepoStatus repoStatus = loadStatus(root);
      cache.put(root, new Snapshot(repoStatus, clock.instant().plus(ttl()), clock));
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
    return dslProperties.git() != null && dslProperties.git().enabled();
  }

  private Path repositoryRoot(Path candidateDir) {
    String configured = dslProperties.git() != null
            ? dslProperties.git().repositoryDir()
            : null;
    return configured != null && !configured.isBlank()
            ? Path.of(configured).toAbsolutePath().normalize()
            : candidateDir.toAbsolutePath().normalize();
  }

  private Duration ttl() {
    int seconds = dslProperties.git() != null
            ? dslProperties.git().statusCacheTtlSeconds()
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
   * ({@code dirtyPaths}), and the typed classification per path ({@code changes}). Backward
   * compatible: the legacy 2-arg constructor leaves {@code changes} empty.
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

  /**
   * Find a git change key {@code K} in {@code changes} that matches the resolved source path
   * {@code P}. Match rule: {@code K.equals(P)}, {@code K.endsWith("/" + P)}, or
   * {@code P.endsWith("/" + K)}. The third clause lets {@code "dsl/LoanDsl.java"} match a builder
   * key like {@code "repo/dsl/LoanDsl.java"}. Shared between the definition-status resolver and the
   * draft publish-flow commit hook (see {@code DslDraftHandler.publishPayload}).
   */
  public static Optional<ChangeType> matchChange(Map<String, ChangeType> changes, String path) {
    if (path == null || path.isBlank() || changes == null || changes.isEmpty()) {
      return Optional.empty();
    }
    ChangeType direct = changes.get(path);
    if (direct != null) {
      return Optional.of(direct);
    }
    String suffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && k.endsWith(suffix)) {
        return Optional.of(e.getValue());
      }
    }
    String pathSuffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && pathSuffix.endsWith("/" + k)) {
        return Optional.of(e.getValue());
      }
    }
    return Optional.empty();
  }

  private DslBuilderClient builderClient() {
    return builderClientProvider == null ? null : builderClientProvider.getIfAvailable();
  }

  private record Snapshot(RepoStatus repoStatus, Instant expiresAt, Clock clock) {
    boolean expired() {
      return clock.instant().isAfter(expiresAt);
    }
  }
}
