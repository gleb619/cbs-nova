package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.VcsModels.CommitRequest;
import cbs.nova.dsl.builder.model.VcsModels.CommitResult;
import cbs.nova.dsl.builder.model.VcsModels.DiscardRequest;
import cbs.nova.dsl.builder.model.VcsModels.DiscardResult;
import cbs.nova.dsl.builder.model.VcsModels.LogEntry;
import cbs.nova.dsl.builder.service.GitStatusService.ChangeType;
import cbs.nova.dsl.builder.service.GitStatusService.RepoStatus;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Git-backed draft operations against the workspace worktree. A draft is an uncommitted git change
 * to a DSL file in the workspace (§3.3 of {@code docs/backend/drafts.md}); publish = commit,
 * discard = checkout HEAD, history = git log.
 */
@Slf4j
@Component
public class WorkspaceGitService {

  private final DslBuilderProperties properties;
  private final Supplier<GitStatusService> gitStatusProvider;

  @Autowired
  public WorkspaceGitService(DslBuilderProperties properties,
          ObjectProvider<GitStatusService> gitStatusProvider) {
    this(properties, gitStatusProvider::getIfAvailable);
  }

  /** Test seam: pass in a fixed supplier so unit tests do not need the Spring context. */
  WorkspaceGitService(DslBuilderProperties properties,
          Supplier<GitStatusService> gitStatusProvider) {
    this.properties = properties;
    this.gitStatusProvider = gitStatusProvider;
  }

  /**
   * Atomic per-request commit of the supplied paths. {@link BuilderApiException} is thrown for
   * {@code 400 INVALID_PATH}, {@code 409 NOTHING_TO_COMMIT}, {@code 409 GIT_NOT_CONFIGURED}, or
   * {@code 500 COMMIT_FAILED}.
   */
  public CommitResult commit(CommitRequest request) {
    if (request == null) {
      throw invalid("commit request body is required");
    }
    return commit(request.paths(), request.message(), request.authorName(), request.authorEmail());
  }

  /**
   * Atomic per-request commit of the supplied paths. All paths must be a current git change per
   * {@link GitStatusService}; if any isn't, the whole request is rejected with
   * {@code NOTHING_TO_COMMIT}. On failure mid-commit, every staged path is reset back to HEAD
   * (working tree untouched) and the exception is rethrown.
   */
  public CommitResult commit(List<String> rawPaths, String message, String authorName,
          String authorEmail) {
    if (message == null || message.isBlank()) {
      throw invalid("commit message is required");
    }
    if (authorName == null || authorName.isBlank()) {
      throw invalid("authorName is required");
    }
    if (authorEmail == null || authorEmail.isBlank()) {
      throw invalid("authorEmail is required");
    }
    List<String> paths = sanitizeAll(rawPaths, "paths list is required");
    RepoStatus status = requireStatus();
    Path repoRoot = findRepoRoot();
    Path workspaceRoot = workspaceRoot();
    List<String> repoPaths = toRepoPaths(paths);

    Set<String> dirtyRepo = new LinkedHashSet<>(status.dirtyPaths());
    Set<String> cleanRepo = new LinkedHashSet<>();
    for (String rp : repoPaths) {
      if (!dirtyRepo.contains(rp)) {
        cleanRepo.add(rp);
      }
    }
    if (!cleanRepo.isEmpty()) {
      List<String> cleanWorkspace = new ArrayList<>();
      for (String rp : cleanRepo) {
        cleanWorkspace.add(toWorkspacePath(rp, repoRoot, workspaceRoot));
      }
      throw new BuilderApiException(HttpStatus.CONFLICT, "NOTHING_TO_COMMIT",
              "no git change for path(s): " + String.join(", ", cleanWorkspace));
    }

    try (Repository repository = openRepository();
            Git git = new Git(repository)) {
      for (String repoPath : repoPaths) {
        Path absolute = workspaceRoot.resolve(repoPath.replace('/', File.separatorChar));
        if (Files.exists(absolute)) {
          AddCommand add = git.add().addFilepattern(repoPath);
          add.call();
        } else {
          git.rm().setCached(true).addFilepattern(repoPath).call();
        }
      }
      CommitCommand cmd = git.commit()
              .setMessage(message)
              .setAuthor(authorName, authorEmail)
              .setCommitter(authorName, authorEmail);
      // JGit 7.x collapses the old varargs into a single path per call; chain setOnly so all
      // requested paths get restricted into one commit and unrelated staged files stay out.
      for (String repoPath : repoPaths) {
        cmd.setOnly(repoPath);
      }
      RevCommit commit;
      try {
        commit = executeCommit(cmd);
      } catch (Exception commitFailure) {
        try {
          ResetCommand reset = git.reset();
          for (String repoPath : repoPaths) {
            reset.addPath(repoPath);
          }
          reset.call();
        } catch (Exception resetFailure) {
          log.warn("[DSL vcs] failed to reset index after commit failure: {}",
                  resetFailure.getMessage());
        }
        throw new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMMIT_FAILED",
                commitFailure.getMessage());
      }
      invalidateGitStatus();
      return new CommitResult(commit.getName(), paths, commit.getCommitTime() * 1000L);
    } catch (BuilderApiException e) {
      throw e;
    } catch (IOException | GitAPIException e) {
      throw new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VCS_ERROR", e.getMessage());
    }
  }

  /** Test seam: lets a unit test force a failure between staging and final commit return. */
  protected RevCommit executeCommit(CommitCommand command) throws GitAPIException {
    return command.call();
  }

  /** Discard = checkout HEAD per path, or remove an untracked / added file. */
  public DiscardResult discard(DiscardRequest request) {
    if (request == null) {
      throw invalid("discard request body is required");
    }
    return discard(request.paths());
  }

  public DiscardResult discard(List<String> rawPaths) {
    List<String> paths = sanitizeAll(rawPaths, "paths list is required");
    if (paths.isEmpty()) {
      return new DiscardResult(List.of());
    }
    RepoStatus status = requireStatus();
    Path repoRoot = findRepoRoot();
    Path workspaceRoot = workspaceRoot();
    Map<ChangeType, List<String>> typedRepo = new HashMap<>();
    for (String repoPath : toRepoPaths(paths)) {
      ChangeType type = status.changes().getOrDefault(repoPath, null);
      if (type != null) {
        typedRepo.computeIfAbsent(type, k -> new ArrayList<>()).add(repoPath);
      }
    }
    if (typedRepo.isEmpty()) {
      // No path in the request is a git change — equivalent to a no-op discard.
      invalidateGitStatus();
      return new DiscardResult(List.of());
    }
    List<String> discarded = new ArrayList<>();
    try (Repository repository = openRepository();
            Git git = new Git(repository)) {
      List<String> checkoutPaths = new ArrayList<>();
      checkoutPaths.addAll(typedRepo.getOrDefault(ChangeType.MODIFIED, List.of()));
      checkoutPaths.addAll(typedRepo.getOrDefault(ChangeType.DELETED, List.of()));
      if (!checkoutPaths.isEmpty()) {
        var checkoutCmd = git.checkout().setStartPoint("HEAD");
        for (String checkoutPath : checkoutPaths) {
          checkoutCmd.addPath(checkoutPath);
        }
        checkoutCmd.call();
        discarded.addAll(checkoutPaths);
      }
      List<String> addedRepoPaths = typedRepo.getOrDefault(ChangeType.ADDED, List.of());
      if (!addedRepoPaths.isEmpty()) {
        var rmCmd = git.rm().setCached(true);
        for (String addedRepoPath : addedRepoPaths) {
          rmCmd.addFilepattern(addedRepoPath);
        }
        rmCmd.call();
        for (String repoPath : addedRepoPaths) {
          Path absolute = workspaceRoot.resolve(repoPath.replace('/', File.separatorChar));
          Files.deleteIfExists(absolute);
        }
        discarded.addAll(addedRepoPaths);
      }
      List<String> untrackedRepoPaths = typedRepo.getOrDefault(ChangeType.UNTRACKED, List.of());
      for (String repoPath : untrackedRepoPaths) {
        Path absolute = workspaceRoot.resolve(repoPath.replace('/', File.separatorChar));
        Files.deleteIfExists(absolute);
        discarded.add(repoPath);
      }
    } catch (IOException | GitAPIException e) {
      throw new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VCS_ERROR", e.getMessage());
    }
    invalidateGitStatus();
    List<String> discardedWorkspace = new ArrayList<>();
    for (String rp : discarded) {
      discardedWorkspace.add(toWorkspacePath(rp, repoRoot, workspaceRoot));
    }
    return new DiscardResult(discardedWorkspace);
  }

  /** Return log entries for {@code path} newest-first. Empty when git is not configured. */
  public List<LogEntry> log(String rawPath, int limit) {
    int safeLimit = Math.min(200, Math.max(1, limit));
    String path = sanitize(rawPath, "path is required");
    if (!gitEnabled()) {
      return List.of();
    }
    Path repoRoot = findRepoRoot();
    if (repoRoot == null) {
      return List.of();
    }
    String repoPath = toRepoPath(path, repoRoot, workspaceRoot());
    try (Repository repository = openRepository();
            Git git = new Git(repository)) {
      List<LogEntry> entries = new ArrayList<>();
      for (RevCommit commit : git.log().addPath(repoPath).setMaxCount(safeLimit).call()) {
        PersonIdent author = commit.getAuthorIdent();
        String authorName = author != null
                ? "%s <%s>".formatted(author.getName(), author.getEmailAddress())
                : "";
        entries.add(new LogEntry(commit.getName(),
                commit.getCommitTime() * 1000L,
                authorName,
                commit.getShortMessage()));
      }
      return entries;
    } catch (IOException | GitAPIException e) {
      throw new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VCS_ERROR", e.getMessage());
    }
  }

  /** Return the file content at {@code commitId} for {@code path}, empty if absent. */
  public Optional<String> show(String rawPath, String commitId) {
    String path = sanitize(rawPath, "path is required");
    if (commitId == null || commitId.isBlank()) {
      throw invalid("commitId is required");
    }
    if (!gitEnabled()) {
      return Optional.empty();
    }
    Path repoRoot = findRepoRoot();
    if (repoRoot == null) {
      return Optional.empty();
    }
    String repoPath = toRepoPath(path, repoRoot, workspaceRoot());
    try (Repository repository = openRepository();
            RevWalk walk = new RevWalk(repository)) {
      RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
      try (TreeWalk treeWalk = new TreeWalk(repository)) {
        treeWalk.addTree(commit.getTree());
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(repoPath));
        if (!treeWalk.next()) {
          return Optional.empty();
        }
        ObjectId blobId = treeWalk.getObjectId(0);
        try (ObjectReader reader = repository.newObjectReader()) {
          ObjectLoader loader = reader.open(blobId);
          byte[] bytes = loader.getBytes();
          return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
      }
    } catch (org.eclipse.jgit.errors.MissingObjectException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VCS_ERROR", e.getMessage());
    }
  }

  // --- path / repo helpers ---

  private void invalidateGitStatus() {
    GitStatusService service = gitStatusProvider != null ? gitStatusProvider.get() : null;
    if (service != null) {
      service.invalidate();
    }
  }

  private boolean gitEnabled() {
    return properties.git() != null && properties.git().enabled();
  }

  private Path workspaceRoot() {
    Path root = properties.workspaceDir();
    if (root == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "cbs.dsl.builder.workspace-dir is not configured");
    }
    return root.toAbsolutePath().normalize();
  }

  /** Resolve the repo root: configured repo dir else {@code findGitDir(workspaceDir)}. */
  private @Nullable Path findRepoRoot() {
    Path workspace = workspaceRoot();
    String configured = properties.git() != null ? properties.git().repositoryDir() : null;
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured).toAbsolutePath().normalize();
    }
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(workspace.toFile());
    if (builder.getGitDir() == null) {
      return null;
    }
    try (Repository repository = builder.build()) {
      return repository.getWorkTree().toPath().toAbsolutePath().normalize();
    } catch (IOException e) {
      return null;
    }
  }

  private Repository openRepository() throws IOException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder()
            .findGitDir(workspaceRoot().toFile());
    if (builder.getGitDir() == null) {
      throw new IOException("no git repository found under workspace");
    }
    return builder.build();
  }

  private RepoStatus requireStatus() {
    if (!gitEnabled()) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "git is disabled in builder configuration");
    }
    Path root = workspaceRoot();
    Path repoRoot = findRepoRoot();
    if (repoRoot == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "no git repository found under workspace");
    }
    return gitStatusProvider.get().status(root)
            .orElseThrow(() -> new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
                    "no git repository found under workspace"));
  }

  private List<String> sanitizeAll(List<String> rawPaths, String emptyMessage) {
    if (rawPaths == null || rawPaths.isEmpty()) {
      throw invalid(emptyMessage);
    }
    List<String> cleaned = new ArrayList<>(rawPaths.size());
    for (String raw : rawPaths) {
      cleaned.add(sanitize(raw, "path is required"));
    }
    return cleaned;
  }

  /**
   * Normalize a path: forward-slashes, strip leading separators, reject blank or {@code ..}
   * segments. Throws {@link BuilderApiException} {@code 400 INVALID_PATH} on rejection.
   */
  static String sanitize(String raw, String message) {
    if (raw == null || raw.isBlank()) {
      throw invalid(message);
    }
    String sanitized = raw.replace('\\', '/').replaceAll("^/+", "");
    if (sanitized.isBlank() || sanitized.contains("..")) {
      throw invalid("path escapes workspace: " + raw);
    }
    return sanitized;
  }

  private static BuilderApiException invalid(String message) {
    return new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH", message);
  }

  private List<String> toRepoPaths(List<String> workspacePaths) {
    Path repoRoot = findRepoRoot();
    Path workspaceRoot = workspaceRoot();
    List<String> repoPaths = new ArrayList<>(workspacePaths.size());
    for (String wp : workspacePaths) {
      repoPaths.add(toRepoPath(wp, repoRoot, workspaceRoot));
    }
    return repoPaths;
  }

  private static String toRepoPath(String workspacePath, Path repoRoot, Path workspaceRoot) {
    if (repoRoot.equals(workspaceRoot)) {
      return workspacePath;
    }
    Path subPath = repoRoot.relativize(workspaceRoot);
    String prefix = subPath.toString().replace('\\', '/');
    if (prefix.isBlank() || ".".equals(prefix)) {
      return workspacePath;
    }
    return prefix + "/" + workspacePath;
  }

  private static String toWorkspacePath(String repoPath, Path repoRoot, Path workspaceRoot) {
    if (repoRoot.equals(workspaceRoot)) {
      return repoPath;
    }
    Path subPath = repoRoot.relativize(workspaceRoot);
    String prefix = subPath.toString().replace('\\', '/');
    if (prefix.isBlank() || ".".equals(prefix)) {
      return repoPath;
    }
    String prefixSlash = prefix + "/";
    if (repoPath.startsWith(prefixSlash)) {
      return repoPath.substring(prefixSlash.length());
    }
    return repoPath;
  }
}
