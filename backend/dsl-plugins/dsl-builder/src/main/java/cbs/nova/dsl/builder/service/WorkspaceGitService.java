package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.config.DslBuilderProperties.PullStrategy;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.VcsModels.CommitRequest;
import cbs.nova.dsl.builder.model.VcsModels.CommitResult;
import cbs.nova.dsl.builder.model.VcsModels.DiscardRequest;
import cbs.nova.dsl.builder.model.VcsModels.DiscardResult;
import cbs.nova.dsl.builder.model.VcsModels.FetchResult;
import cbs.nova.dsl.builder.model.VcsModels.LogEntry;
import cbs.nova.dsl.builder.model.VcsModels.PullResult;
import cbs.nova.dsl.builder.model.VcsModels.RebaseResult;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
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
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
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
  private final WorkspaceService workspaceService;
  private final Supplier<GitStatusService> gitStatusProvider;

  @Autowired
  @Deprecated(forRemoval = true)
  // TODO: repalce ctor with a lombok's one
  public WorkspaceGitService(DslBuilderProperties properties, WorkspaceService workspaceService,
          ObjectProvider<GitStatusService> gitStatusProvider) {
    this(properties, workspaceService, gitStatusProvider::getIfAvailable);
  }

  /** Test seam: pass in a fixed supplier so unit tests do not need the Spring context. */
  @Deprecated(forRemoval = true)
  // TODO: repalce ctor with a lombok's one
  WorkspaceGitService(DslBuilderProperties properties, WorkspaceService workspaceService,
          Supplier<GitStatusService> gitStatusProvider) {
    this.properties = properties;
    this.workspaceService = workspaceService;
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
      Boolean pushed = null;
      String pushError = null;
      if (properties.git() != null && properties.git().pushOnCommit()) {
        PushResult pushResult = push();
        pushed = pushResult.ok();
        pushError = pushResult.message();
      }
      return new CommitResult(commit.getName(), paths, commit.getCommitTime() * 1000L,
              pushed, pushError);
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

  /**
   * Current branch of the workspace's git repository. Empty when git is disabled or no repo is
   * configured under the workspace. Never throws — the dashboard treats a missing value as "no
   * branch info available" rather than a 500.
   */
  public Optional<String> branch() {
    if (!gitEnabled()) {
      return Optional.empty();
    }
    Path repoRoot = findRepoRoot();
    if (repoRoot == null) {
      return Optional.empty();
    }
    try (Repository repository = openRepository()) {
      String name = repository.getBranch();
      return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(name);
    } catch (IOException e) {
      log.debug("[DSL vcs] failed to read branch from {}: {}", repoRoot, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Push the current branch to the configured {@code cbs.dsl.builder.git.remote}. Never
   * force-pushes. Returns a {@link PushResult} with {@code ok=false} and a message when any remote
   * ref update is not OK / up-to-date.
   */
  public PushResult push() {
    if (!gitEnabled()) {
      return new PushResult(false, "git is disabled in builder configuration");
    }
    Path repoRoot = findRepoRoot();
    if (repoRoot == null) {
      return new PushResult(false, "no git repository found under workspace");
    }
    String remote = properties.git() != null ? properties.git().remote() : null;
    if (remote == null || remote.isBlank()) {
      return new PushResult(false, "remote is not configured");
    }
    try (Repository repository = openRepository();
            Git git = new Git(repository)) {
      String branch = repository.getBranch();
      Iterable<org.eclipse.jgit.transport.PushResult> results = git.push()
              .setRemote(remote)
              .add(branch)
              .call();
      for (org.eclipse.jgit.transport.PushResult result : results) {
        for (RemoteRefUpdate update : result.getRemoteUpdates()) {
          RemoteRefUpdate.Status status = update.getStatus();
          if (status != RemoteRefUpdate.Status.OK
                  && status != RemoteRefUpdate.Status.UP_TO_DATE) {
            String detail = update.getMessage() != null ? ": " + update.getMessage() : "";
            return new PushResult(false,
                    "push to " + remote + "/" + update.getRemoteName() + " failed: "
                            + status.name() + detail);
          }
        }
      }
      return new PushResult(true, null);
    } catch (IOException | GitAPIException e) {
      log.warn("[DSL vcs] push to {} failed: {}", remote, e.getMessage());
      return new PushResult(false, "push failed: " + e.getMessage());
    }
  }

  /** Result of a {@link #push()}: ok + an optional message (null on success). */
  public record PushResult(boolean ok, String message) {
  }

  /**
   * Fetch the configured remote for the active branch. Refreshes the remote-tracking ref and
   * reports how far ahead/behind the workspace branch is relative to {@code origin/<branch>}.
   */
  public FetchResult fetch() {
    try (Repository repository = openConfiguredRepository();
            Git git = new Git(repository)) {
      String branch = requireBranch(repository);
      String remote = requireRemote(repository);
      var fetchResult = git.fetch().setRemote(remote).call();
      ObjectId upstream = repository.resolve(upstreamRef(remote, branch));
      int[] ab = upstream != null ? aheadBehind(repository, "HEAD", upstream) : new int[]{0, 0};
      invalidateGitStatus();
      return new FetchResult(branch, !fetchResult.getTrackingRefUpdates().isEmpty(),
              upstream != null ? upstream.getName() : null, ab[0], ab[1]);
    } catch (IOException | GitAPIException e) {
      throw vcsError(e);
    }
  }

  /**
   * Pull {@code origin/<branch>} into the workspace branch. Up to date is a no-op; strictly behind
   * fast-forwards; on divergence the configured {@code cbs.dsl.builder.git.pull-strategy} applies
   * (default {@link PullStrategy#REBASE}). A rebase conflict aborts the rebase and leaves the head
   * and working tree untouched; a merge conflict follows git-native behaviour and stays in progress
   * for manual resolution. Never forces.
   */
  public PullResult pull() {
    try (Repository repository = openConfiguredRepository();
            Git git = new Git(repository)) {
      String branch = requireBranch(repository);
      String remote = requireRemote(repository);
      ObjectId upstream = fetchUpstream(git, repository, remote, branch);
      PullStrategy strategy = properties.git().pullStrategy();
      String headBefore = headOf(repository);
      int[] before = aheadBehind(repository, "HEAD", upstream);
      PullResult result;
      if (before[1] == 0) {
        result = new PullResult(branch, strategy.name(), false, null, null, before[0], 0, null);
      } else if (before[0] == 0) {
        result = fastForwardPull(git, repository, branch, strategy, upstream, headBefore, before);
      } else if (strategy == PullStrategy.REBASE) {
        result = rebasePull(git, repository, branch, upstream, headBefore, before);
      } else {
        result = mergePull(git, repository, branch, remote, upstream, headBefore, before);
      }
      invalidateGitStatus();
      return result;
    } catch (BuilderApiException e) {
      throw e;
    } catch (IOException | GitAPIException e) {
      throw vcsError(e);
    }
  }

  /**
   * Rebase the workspace branch onto {@code origin/<branch>} after refreshing the remote-tracking
   * ref. A conflicted rebase is aborted: the head and working tree stay as they were and the result
   * carries a {@code message}. Never forces.
   */
  public RebaseResult rebase() {
    try (Repository repository = openConfiguredRepository();
            Git git = new Git(repository)) {
      String branch = requireBranch(repository);
      String remote = requireRemote(repository);
      ObjectId upstream = fetchUpstream(git, repository, remote, branch);
      String headBefore = headOf(repository);
      int[] before = aheadBehind(repository, "HEAD", upstream);
      org.eclipse.jgit.api.RebaseResult rebase = git.rebase().setUpstream(upstream).call();
      org.eclipse.jgit.api.RebaseResult.Status status = rebase.getStatus();
      RebaseResult result;
      if (status == org.eclipse.jgit.api.RebaseResult.Status.OK
              || status == org.eclipse.jgit.api.RebaseResult.Status.FAST_FORWARD) {
        int[] ab = aheadBehind(repository, "HEAD", upstream);
        result = new RebaseResult(branch, true, headBefore, headOf(repository), ab[0], ab[1],
                null);
      } else if (status == org.eclipse.jgit.api.RebaseResult.Status.UP_TO_DATE) {
        result = new RebaseResult(branch, false, null, null, before[0], before[1], null);
      } else {
        List<String> conflicts = conflictingPaths(git, rebase);
        abortRebase(git);
        result = new RebaseResult(branch, false, null, null, before[0], before[1],
                conflictMessage("rebase", status, conflicts));
      }
      invalidateGitStatus();
      return result;
    } catch (BuilderApiException e) {
      throw e;
    } catch (IOException | GitAPIException e) {
      throw vcsError(e);
    }
  }

  private PullResult fastForwardPull(Git git, Repository repository, String branch,
          PullStrategy strategy, ObjectId upstream, String headBefore, int[] before)
          throws GitAPIException, IOException {
    MergeResult merge = git.merge().include(upstream)
            .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
            .call();
    if (merge.getMergeStatus().isSuccessful()) {
      return movedPullResult(repository, branch, strategy, upstream, headBefore);
    }
    return new PullResult(branch, strategy.name(), false, null, null, before[0], before[1],
            "fast-forward refused: " + merge.getMergeStatus());
  }

  private PullResult rebasePull(Git git, Repository repository, String branch, ObjectId upstream,
          String headBefore, int[] before) throws GitAPIException, IOException {
    org.eclipse.jgit.api.RebaseResult rebase = git.rebase().setUpstream(upstream).call();
    org.eclipse.jgit.api.RebaseResult.Status status = rebase.getStatus();
    if (status == org.eclipse.jgit.api.RebaseResult.Status.OK
            || status == org.eclipse.jgit.api.RebaseResult.Status.FAST_FORWARD) {
      return movedPullResult(repository, branch, PullStrategy.REBASE, upstream, headBefore);
    }
    List<String> conflicts = conflictingPaths(git, rebase);
    abortRebase(git);
    return new PullResult(branch, PullStrategy.REBASE.name(), false, null, null,
            before[0], before[1],
            conflictMessage("rebase", status, conflicts));
  }

  private PullResult mergePull(Git git, Repository repository, String branch, String remote,
          ObjectId upstream, String headBefore, int[] before) throws GitAPIException, IOException {
    MergeResult merge = git.merge().include(upstream)
            .setMessage("Merge " + remote + "/" + branch + " into " + branch)
            .call();
    if (merge.getMergeStatus().isSuccessful()) {
      return movedPullResult(repository, branch, PullStrategy.MERGE, upstream, headBefore);
    }
    // Git-native behaviour: the merge stays in progress with conflict markers for the user to
    // resolve and commit — nothing is reset and nothing is forced.
    Map<String, int[][]> conflicts = merge.getConflicts();
    String detail = conflicts == null || conflicts.isEmpty()
            ? ""
            : " in: " + String.join(", ", conflicts.keySet());
    return new PullResult(branch, PullStrategy.MERGE.name(), false, null, null,
            before[0], before[1], "merge conflict" + detail + " — resolve and commit");
  }

  private PullResult movedPullResult(Repository repository, String branch, PullStrategy strategy,
          ObjectId upstream, String headBefore) throws IOException {
    int[] ab = aheadBehind(repository, "HEAD", upstream);
    return new PullResult(branch, strategy.name(), true, headBefore, headOf(repository),
            ab[0], ab[1], null);
  }

  private static void abortRebase(Git git) {
    try {
      git.rebase().setOperation(RebaseCommand.Operation.ABORT).call();
    } catch (GitAPIException e) {
      log.warn("[DSL vcs] failed to abort rebase: {}", e.getMessage());
    }
  }

  private static String conflictMessage(String operation,
          org.eclipse.jgit.api.RebaseResult.Status status, List<String> conflicts) {
    if (conflicts != null && !conflicts.isEmpty()) {
      return operation + " stopped on conflict in: " + String.join(", ", conflicts)
              + " — local changes preserved";
    }
    return operation + " stopped (" + status + ") — local changes preserved";
  }

  private static List<String> conflictingPaths(Git git,
          org.eclipse.jgit.api.RebaseResult rebase) {
    List<String> conflicts = rebase.getConflicts();
    if (conflicts != null && !conflicts.isEmpty()) {
      return conflicts;
    }
    try {
      return List.copyOf(git.status().call().getConflicting());
    } catch (GitAPIException e) {
      return List.of();
    }
  }

  private ObjectId fetchUpstream(Git git, Repository repository, String remote, String branch)
          throws GitAPIException, IOException {
    git.fetch().setRemote(remote).call();
    ObjectId upstream = repository.resolve(upstreamRef(remote, branch));
    if (upstream == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "VCS_ERROR",
              "no upstream ref " + remote + "/" + branch + " after fetch");
    }
    return upstream;
  }

  private static String upstreamRef(String remote, String branch) {
    return "refs/remotes/" + remote + "/" + branch;
  }

  private static int[] aheadBehind(Repository repository, String fromRef, ObjectId to)
          throws IOException {
    // RevWalkUtils.count(walk, a, b) counts commits reachable from a but not b.
    try (RevWalk walk = new RevWalk(repository)) {
      RevCommit from = walk.parseCommit(repository.resolve(fromRef));
      RevCommit upstream = walk.parseCommit(to);
      return new int[]{RevWalkUtils.count(walk, from, upstream),
          RevWalkUtils.count(walk, upstream, from)};
    }
  }

  private static String headOf(Repository repository) throws IOException {
    ObjectId head = repository.resolve("HEAD");
    return head != null ? head.getName() : null;
  }

  private Repository openConfiguredRepository() {
    if (!gitEnabled()) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "git is disabled in builder configuration");
    }
    if (findRepoRoot() == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "no git repository found under workspace");
    }
    try {
      return openRepository();
    } catch (IOException e) {
      throw vcsError(e);
    }
  }

  private static String requireBranch(Repository repository) throws IOException {
    String branch = repository.getBranch();
    if (branch == null || branch.isBlank()) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "VCS_ERROR",
              "workspace repository is on a detached HEAD");
    }
    return branch;
  }

  private String requireRemote(Repository repository) {
    String remote = properties.git() != null ? properties.git().remote() : null;
    if (remote == null || remote.isBlank()
            || repository.getConfig().getString("remote", remote, "url") == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "remote '" + remote + "' is not configured in the workspace repository");
    }
    return remote;
  }

  private static BuilderApiException vcsError(Exception e) {
    return new BuilderApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VCS_ERROR",
            e.getMessage());
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
    try {
      return workspaceService.workTree();
    } catch (IllegalStateException e) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "GIT_NOT_CONFIGURED",
              "cbs.dsl.builder.workspace-dir is not configured");
    }
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
