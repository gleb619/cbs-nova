package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.config.WorkspaceMode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Source of truth for the directory that backs DSL file and git I/O (docs/backend/drafts.md §1).
 *
 * <p>
 * {@link WorkspaceMode#SHARED}: the work tree is {@code cbs.dsl.builder.source-dir} when set,
 * otherwise {@code cbs.dsl.builder.workspace-dir} — exactly the directory the starter's
 * {@code cbs.dsl.source-dir} points at in dev/local deployments. No cloning, no directory creation
 * beyond what file writes need; git detection simply reports {@code GIT_NOT_CONFIGURED} when the
 * directory is not inside a repository.
 *
 * <p>
 * {@link WorkspaceMode#WORKTREE}: the work tree is {@code cbs.dsl.builder.workspace-dir}, managed
 * by the builder. When {@code cbs.dsl.builder.git.repo-url} is configured and the workspace holds
 * no git repository yet, the builder clones the repository at startup (existing {@link GitService}
 * logic) so drafts and publishes work out of the box.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

  private final DslBuilderProperties properties;
  private final GitService gitService;

  public WorkspaceMode mode() {
    return properties.workspaceMode();
  }

  /**
   * Absolute, normalized path of the directory backing file reads/writes and git operations on this
   * builder.
   */
  public Path workTree() {
    Path configured = properties.workspaceMode() == WorkspaceMode.SHARED
            && properties.sourceDir() != null
                    ? properties.sourceDir()
                    : properties.workspaceDir();
    if (configured == null) {
      throw new IllegalStateException(
              "cbs.dsl.builder.workspace-dir is not configured");
    }
    return configured.toAbsolutePath().normalize();
  }

  /** Workspace description exposed via {@code GET /api/dsl/workspace}. */
  public WorkspaceInfo workspaceInfo() {
    return new WorkspaceInfo(mode().name(), workTree().toString().replace('\\', '/'));
  }

  @PostConstruct
  void provisionWorktree() {
    if (properties.workspaceMode() != WorkspaceMode.WORKTREE || !gitEnabled()) {
      return;
    }
    String repoUrl = properties.git().repoUrl();
    if (repoUrl == null || repoUrl.isBlank()) {
      return;
    }
    Path workspace = workTree();
    if (hasGitRepository(workspace) || !isEmptyDirectory(workspace)) {
      return;
    }
    try {
      Files.createDirectories(workspace);
      gitService.cloneRepository(repoUrl, workspace, properties.git().branch());
      log.info("[DSL workspace] provisioned worktree {} from {} (branch={})", workspace, repoUrl,
              properties.git().branch());
    } catch (RuntimeException | IOException e) {
      log.warn("[DSL workspace] failed to provision worktree {} from {}: {}", workspace, repoUrl,
              e.getMessage());
    }
  }

  private boolean gitEnabled() {
    return properties.git() != null && properties.git().enabled();
  }

  private static boolean hasGitRepository(Path dir) {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    if (Files.isDirectory(dir.resolve(".git"))) {
      return true;
    }
    try (Stream<Path> walk = Files.walk(dir, 3)) {
      return walk.anyMatch(path -> Files.isDirectory(path)
              && (path.getFileName().toString().equals(".git")
                      || path.getFileName().toString().endsWith(".git")));
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean isEmptyDirectory(Path dir) {
    if (!Files.isDirectory(dir)) {
      return true;
    }
    try (Stream<Path> walk = Files.list(dir)) {
      return walk.findAny().isEmpty();
    } catch (IOException e) {
      return false;
    }
  }

  /** Response shape for {@code GET /api/dsl/workspace}. */
  public record WorkspaceInfo(String mode, String workTree) {
  }
}
