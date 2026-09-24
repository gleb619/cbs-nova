package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.VcsModels.CommitRequest;
import cbs.nova.dsl.builder.model.VcsModels.CommitResult;
import cbs.nova.dsl.builder.model.VcsModels.DiscardRequest;
import cbs.nova.dsl.builder.model.VcsModels.DiscardResult;
import cbs.nova.dsl.builder.model.VcsModels.LogEntry;
import cbs.nova.dsl.builder.service.FileService;
import cbs.nova.dsl.builder.service.GitStatusService;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.dsl.builder.service.WorkspaceGitService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/vcs")
public class VcsController {

  private final GitStatusService gitStatusService;
  private final WorkspaceGitService workspaceGitService;
  private final DslBuilderProperties properties;
  private final FileService fileService;

  @GetMapping("/status")
  public RepoStatus status() {
    // Invariant I5: a pending write already counts as a draft. Flush the buffer first, then
    // drop the per-root snapshot so the next scan reflects the freshly-flushed file. The
    // flush itself also invalidates the cache, but we invalidate again here in case another
    // writer mutated the working tree in the meantime (e.g. the scheduled flusher).
    if (fileService.pendingCount() > 0) {
      fileService.flushPending();
      gitStatusService.invalidate();
    }
    return gitStatusService.status(properties.workspaceDir())
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no git repository found under workspace"));
  }

  /** Atomic per-request publish (§3.3 + I2): commit the dirty paths and refresh the cache. */
  @PostMapping("/commit")
  public CommitResult commit(@RequestBody CommitRequest body) {
    if (body == null || body.paths() == null || body.paths().isEmpty()) {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH",
              "paths list is required");
    }
    if (body.message() == null || body.message().isBlank()) {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH",
              "commit message is required");
    }
    flushPendingDrafts();
    return workspaceGitService.commit(body);
  }

  /** Discard = checkout HEAD (or remove untracked / added file) per path. */
  @PostMapping("/discard")
  public DiscardResult discard(@RequestBody DiscardRequest body) {
    if (body == null || body.paths() == null || body.paths().isEmpty()) {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH",
              "paths list is required");
    }
    flushPendingDrafts();
    return workspaceGitService.discard(body);
  }

  /** History for a path, newest-first. {@code limit} clamps 1..200. */
  @GetMapping("/log")
  public List<LogEntry> log(@RequestParam @NotBlank String path,
          @RequestParam(defaultValue = "20") int limit) {
    return workspaceGitService.log(path, Math.min(200, Math.max(1, limit)));
  }

  /** File content at a commit. 404 when the path is not in that tree. */
  @GetMapping("/show")
  public ShowResponse show(@RequestParam @NotBlank String path,
          @RequestParam @NotBlank String commit) {
    Optional<String> content = workspaceGitService.show(path, commit);
    return content.map(c -> new ShowResponse(path, commit, c))
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no file at " + commit + ":" + path));
  }

  /**
   * Current branch of the workspace's git repository, or {@code {"branch": null}} when git is
   * disabled or no repository is configured. Never throws — the starter dashboard treats a missing
   * branch as "no info" rather than a 5xx.
   */
  @GetMapping("/branch")
  public BranchResponse branch() {
    return new BranchResponse(workspaceGitService.branch().orElse(null));
  }

  private void flushPendingDrafts() {
    // I5: a just-buffered edit is already a draft. Flush it before commit/discard so its
    // content is on disk for JGit to stage; the FileService also drops the git-status cache.
    if (fileService.pendingCount() > 0) {
      fileService.flushPending();
      gitStatusService.invalidate();
    }
  }

  /** Inline response shape for {@code GET /api/dsl/vcs/show}. */
  public record ShowResponse(String path, String commitId, String content) {
  }

  /** Inline response shape for {@code GET /api/dsl/vcs/branch}. */
  public record BranchResponse(String branch) {
  }
}
