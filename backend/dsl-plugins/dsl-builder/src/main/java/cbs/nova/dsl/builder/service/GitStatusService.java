package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.GitChangeClassifier;
import cbs.nova.dsl.vcs.RepoStatus;
import java.io.IOException;
import java.nio.file.Files;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitStatusService {

  private final DslBuilderProperties properties;
  private final Clock clock;
  private final ConcurrentHashMap<Path, Snapshot> cache = new ConcurrentHashMap<>();

  @Autowired
  public GitStatusService(DslBuilderProperties properties) {
    this(properties, Clock.systemUTC());
  }

  /**
   * Drop every per-root cached snapshot so the next {@link #status(Path)} call rescans git. Called
   * by {@link FileService} after a pending write flush and by {@code VcsController} before a status
   * read so a just-staged edit shows up immediately (gap G6 / invariant I5).
   */
  public void invalidate() {
    cache.clear();
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
      Path workTree = repository.getWorkTree().toPath().toAbsolutePath().normalize();
      return RepoStatus.of(workTree, changes, sizesOf(changes.keySet(), workTree));
    }
  }

  /**
   * On-disk size in bytes for each changed path that still exists as a regular file. Deleted paths
   * contribute nothing; sizes are read right after the JGit scan so a pending-write flush (I5) is
   * reflected.
   */
  private static Map<String, Long> sizesOf(Set<String> changedPaths, Path workTree) {
    Map<String, Long> sizes = new HashMap<>();
    for (String path : changedPaths) {
      try {
        Path file = workTree.resolve(path.replace('\\', '/')).normalize();
        if (file.startsWith(workTree) && Files.isRegularFile(file)) {
          sizes.put(path, Files.size(file));
        }
      } catch (IOException e) {
        log.debug("[DSL git] failed to read size for {}: {}", path, e.getMessage());
      }
    }
    return sizes;
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
   * Classify a JGit {@link Status} into a path→{@link ChangeType} map via the shared
   * {@link GitChangeClassifier}. Later writes win so precedence collapses to
   * {@code CONFLICTING > DELETED > ADDED > UNTRACKED > MODIFIED}.
   */
  static Map<String, ChangeType> classify(Status status) {
    return GitChangeClassifier.classify(
            status.getAdded(),
            status.getChanged(),
            status.getModified(),
            status.getUntracked(),
            status.getRemoved(),
            status.getMissing(),
            status.getConflicting());
  }

  private record Snapshot(RepoStatus repoStatus, Instant expiresAt) {
  }
}
