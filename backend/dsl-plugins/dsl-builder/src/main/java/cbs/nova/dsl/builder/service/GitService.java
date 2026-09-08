package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.exception.CompileException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GitService {

  public Path cloneRepository(
          String repoUrl, Path targetDir, String branch, CredentialsProvider credentials) {
    var command = Git.cloneRepository().setURI(repoUrl).setDirectory(targetDir.toFile());
    if (branch != null && !branch.isBlank()) {
      command.setBranch(branch);
    }
    if (credentials != null) {
      command.setCredentialsProvider(credentials);
    }
    try (Git git = command.call()) {
      return git.getRepository().getWorkTree().toPath();
    } catch (GitAPIException e) {
      throw new CompileException("Failed to clone repository: " + e.getMessage(),
              List.of(e.getMessage()));
    }
  }

  // TODO: wire, add endpoint and use method
  @Deprecated
  public void pull(Path repoDir, CredentialsProvider credentials) {
    try (Git git = Git.open(repoDir.toFile())) {
      var pull = git.pull();
      if (credentials != null) {
        pull.setCredentialsProvider(credentials);
      }
      pull.call();
    } catch (IOException | GitAPIException e) {
      throw new CompileException("Failed to pull repository: " + e.getMessage(),
              List.of(e.getMessage()));
    }
  }

  public Path createWorktree(Path repoDir, Path worktreeDir, String baseBranch) {
    var branch = "dsl-" + UUID.randomUUID();
    var args = new ArrayList<String>();
    args.add("worktree");
    args.add("add");
    args.add("-b");
    args.add(branch);
    args.add(worktreeDir.toString());
    if (baseBranch != null && !baseBranch.isBlank()) {
      args.add(baseBranch);
    }
    runGit(repoDir, args);
    return worktreeDir;
  }

  public List<String> listWorktrees(Path repoDir) {
    return runGit(repoDir, List.of("worktree", "list", "--porcelain")).lines()
            .filter(line -> line.startsWith("worktree "))
            .map(line -> line.substring("worktree ".length()))
            .toList();
  }

  public void removeWorktree(Path repoDir, Path worktreeDir) {
    runGit(repoDir, List.of("worktree", "remove", "--force", worktreeDir.toString()));
  }

  private String runGit(Path repoDir, List<String> args) {
    var command = new ArrayList<String>();
    command.add("git");
    command.add("-C");
    command.add(repoDir.toString());
    command.addAll(args);
    try {
      var process = new ProcessBuilder(command).redirectErrorStream(true).start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.waitFor() != 0) {
        throw new CompileException("git %s failed: %s".formatted(args.getFirst(),
                output.strip()), output.lines().toList());
      }
      return output;
    } catch (IOException e) {
      throw new CompileException("Failed to run git: " + e.getMessage(),
              List.of(e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CompileException("git interrupted", List.of("git interrupted"));
    }
  }
}
