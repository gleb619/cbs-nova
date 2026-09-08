package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitServiceTest {

  @TempDir
  Path tempDir;

  @BeforeAll
  static void checkNativeGit() {
    var available = new ProcessBuilder("git", "--version").redirectErrorStream(true);
    try {
      var process = available.start();
      Assumptions.assumeTrue(process.waitFor() == 0, "native git binary not available");
    } catch (Exception e) {
      Assumptions.assumeTrue(false, "native git binary not available");
    }
  }

  @Test
  void clonesRepository() throws Exception {
    var origin = createRepositoryWithCommit();
    var service = new GitService();

    var clone = service.cloneRepository(origin.toString(), tempDir.resolve("clone"), null, null);

    assertThat(clone.resolve("README.md")).exists();
    assertThat(clone.resolve(".git")).exists();
  }

  @Test
  void createsListsAndRemovesWorktrees() throws Exception {
    var repoDir = createRepositoryWithCommit();
    var service = new GitService();
    var worktreeDir = tempDir.resolve("worktree");

    service.createWorktree(repoDir, worktreeDir, "main");

    assertThat(service.listWorktrees(repoDir)).contains(worktreeDir.toString());
    assertThat(worktreeDir.resolve(".git")).exists();

    service.removeWorktree(repoDir, worktreeDir);

    assertThat(service.listWorktrees(repoDir)).doesNotContain(worktreeDir.toString());
    assertThat(worktreeDir).doesNotExist();
  }

  private Path createRepositoryWithCommit() throws Exception {
    var repoDir = tempDir.resolve("repo-" + java.util.UUID.randomUUID());
    try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
      Files.writeString(repoDir.resolve("README.md"), "test");
      git.add().addFilepattern(".").call();
      git.commit()
              .setMessage("initial")
              .setAuthor("dsl-builder-test", "test@example.com")
              .setCommitter("dsl-builder-test", "test@example.com")
              .call();
    }
    return repoDir;
  }
}
