package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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
      Set<String> dirty = new HashSet<>();
      dirty.addAll(status.getAdded());
      dirty.addAll(status.getChanged());
      dirty.addAll(status.getModified());
      dirty.addAll(status.getUntracked());
      dirty.addAll(status.getRemoved());
      dirty.addAll(status.getMissing());
      return new RepoStatus(
              repository.getWorkTree().toPath().toAbsolutePath().normalize(),
              Set.copyOf(dirty));
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

  public record RepoStatus(Path workTree, Set<String> dirtyPaths) {
  }

  private record Snapshot(RepoStatus repoStatus, Instant expiresAt) {
  }
}
