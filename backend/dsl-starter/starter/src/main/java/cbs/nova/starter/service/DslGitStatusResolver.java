package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslGitStatusResolver {

  private final DslProperties dslProperties;
  private final ConcurrentHashMap<Path, Snapshot> cache = new ConcurrentHashMap<>();

  public Optional<RepoStatus> status(Path candidateDir) {
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
      cache.put(root, new Snapshot(repoStatus, Instant.now().plus(ttl())));
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
    return dslProperties.getGit() != null && dslProperties.getGit().isEnabled();
  }

  private Path repositoryRoot(Path candidateDir) {
    String configured = dslProperties.getGit() != null
            ? dslProperties.getGit().getRepositoryDir()
            : null;
    return configured != null && !configured.isBlank()
            ? Path.of(configured).toAbsolutePath().normalize()
            : candidateDir.toAbsolutePath().normalize();
  }

  private Duration ttl() {
    int seconds = dslProperties.getGit() != null
            ? dslProperties.getGit().getStatusCacheTtlSeconds()
            : 5;
    return Duration.ofSeconds(Math.max(0, seconds));
  }

  public record RepoStatus(Path workTree, Set<String> dirtyPaths) {
  }

  private record Snapshot(RepoStatus repoStatus, Instant expiresAt) {
    boolean expired() {
      return Instant.now().isAfter(expiresAt);
    }
  }
}
