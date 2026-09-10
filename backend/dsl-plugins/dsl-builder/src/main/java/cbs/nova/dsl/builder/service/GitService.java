package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.exception.CompileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GitService {

  public Path cloneRepository(String repoUrl, Path targetDir, String branch) {
    try {
      var clone = Git.cloneRepository().setURI(repoUrl).setDirectory(targetDir.toFile());
      if (branch != null && !branch.isBlank()) {
        clone.setBranch(branch);
      }
      clone.call();
      return targetDir;
    } catch (GitAPIException e) {
      throw compileError("clone", e);
    }
  }

  public void pull(Path repoDir, String branch) {
    try (Git git = open(repoDir)) {
      var pull = git.pull().setFastForward(FastForwardMode.FF_ONLY);
      if (branch != null && !branch.isBlank()) {
        pull.setRemote("origin").setRemoteBranchName(branch);
      }
      pull.call();
    } catch (GitAPIException | IOException e) {
      throw compileError("pull", e);
    }
  }

  public Path createWorktree(Path repoDir, Path worktreeDir, String baseBranch) {
    var branch = "dsl-" + UUID.randomUUID();
    try (Git git = open(repoDir)) {
      var startPoint = baseBranch == null || baseBranch.isBlank() ? Constants.HEAD : baseBranch;
      git.branchCreate().setName(branch).setStartPoint(startPoint).call();
      var adminDir = git.getRepository().getDirectory().toPath()
              .resolve("worktrees").resolve(branch);
      Files.createDirectories(worktreeDir);
      Files.createDirectories(adminDir);
      Files.writeString(adminDir.resolve("commondir"), "../..\n");
      Files.writeString(adminDir.resolve("gitdir"), worktreeDir.resolve(".git") + "\n");
      Files.writeString(adminDir.resolve("HEAD"), "ref: " + Constants.R_HEADS + branch + "\n");
      Files.writeString(worktreeDir.resolve(".git"), "gitdir: " + adminDir + "\n");
      try (Git worktree = open(worktreeDir)) {
        worktree.checkout().setName(branch).call();
      }
      return worktreeDir;
    } catch (GitAPIException | IOException e) {
      throw compileError("worktree add", e);
    }
  }

  public List<String> listWorktrees(Path repoDir) {
    try (Git git = open(repoDir)) {
      var worktreesDir = git.getRepository().getDirectory().toPath().resolve("worktrees");
      if (!Files.isDirectory(worktreesDir)) {
        return List.of();
      }
      try (var stream = Files.list(worktreesDir)) {
        return stream.filter(Files::isDirectory)
                .map(dir -> dir.resolve("gitdir"))
                .filter(Files::isRegularFile)
                .map(this::readWorktreePath)
                .toList();
      }
    } catch (IOException e) {
      throw compileError("worktree list", e);
    }
  }

  public void removeWorktree(Path repoDir, Path worktreeDir) {
    try {
      var gitFile = worktreeDir.resolve(".git");
      if (Files.isRegularFile(gitFile)) {
        var content = Files.readString(gitFile).strip();
        if (content.startsWith("gitdir: ")) {
          deleteRecursively(Path.of(content.substring("gitdir: ".length())));
        }
      }
      deleteRecursively(worktreeDir);
    } catch (IOException e) {
      throw compileError("worktree remove", e);
    }
  }

  private Git open(Path repoDir) throws IOException {
    return Git.open(repoDir.toFile());
  }

  private String readWorktreePath(Path gitdirFile) {
    try {
      var path = Path.of(Files.readString(gitdirFile).strip());
      return path.getParent() == null ? path.toString() : path.getParent().toString();
    } catch (IOException e) {
      throw compileError("worktree list", e);
    }
  }

  private void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      var paths = stream.sorted(Comparator.reverseOrder()).toList();
      for (var path : paths) {
        Files.delete(path);
      }
    }
  }

  private CompileException compileError(String operation, Exception e) {
    log.warn("git {} failed: {}", operation, e.getMessage());
    return new CompileException("git %s failed: %s".formatted(operation, e.getMessage()),
            List.of(e.getMessage()));
  }
}
