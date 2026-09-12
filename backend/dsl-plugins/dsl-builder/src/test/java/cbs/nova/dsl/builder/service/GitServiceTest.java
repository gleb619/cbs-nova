package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void clonesRepository() throws Exception {
    var origin = createRepositoryWithCommit();
    var service = new GitService();

    var clone = service.cloneRepository(origin.toString(), tempDir.resolve("clone"), null);

    assertThat(clone.resolve("README.md")).exists();
    assertThat(clone.resolve(".git")).exists();
  }

  private Path createRepositoryWithCommit() throws Exception {
    var repoDir = tempDir.resolve("repo-" + UUID.randomUUID());
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
