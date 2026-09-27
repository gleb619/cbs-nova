package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.config.WorkspaceMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void sharedModePrefersSourceDirOverWorkspaceDir() {
    Path workspace = tempDir.resolve("workspace");
    Path source = tempDir.resolve("source");
    var service = new WorkspaceService(properties(workspace, source, WorkspaceMode.SHARED),
            new GitService());

    assertThat(service.mode()).isEqualTo(WorkspaceMode.SHARED);
    assertThat(service.workTree()).isEqualTo(source.toAbsolutePath().normalize());
  }

  @Test
  void sharedModeFallsBackToWorkspaceDirWhenSourceDirUnset() {
    Path workspace = tempDir.resolve("workspace");
    var service = new WorkspaceService(properties(workspace, null, WorkspaceMode.SHARED),
            new GitService());

    assertThat(service.workTree()).isEqualTo(workspace.toAbsolutePath().normalize());
  }

  @Test
  void worktreeModeUsesWorkspaceDirEvenWhenSourceDirSet() {
    Path workspace = tempDir.resolve("workspace");
    Path source = tempDir.resolve("source");
    var service = new WorkspaceService(properties(workspace, source, WorkspaceMode.WORKTREE),
            new GitService());

    assertThat(service.mode()).isEqualTo(WorkspaceMode.WORKTREE);
    assertThat(service.workTree()).isEqualTo(workspace.toAbsolutePath().normalize());
  }

  @Test
  void workspaceInfoExposesModeAndNormalizedWorkTree() {
    Path workspace = tempDir.resolve("workspace");
    var service = new WorkspaceService(properties(workspace, null, WorkspaceMode.SHARED),
            new GitService());

    var info = service.workspaceInfo();

    assertThat(info.mode()).isEqualTo("SHARED");
    assertThat(info.workTree()).isEqualTo(workspace.toAbsolutePath().normalize().toString()
            .replace('\\', '/'));
  }

  @Test
  void worktreeModeClonesRepoUrlIntoEmptyWorkspace() throws Exception {
    Path bare = tempDir.resolve("origin.git");
    try (Git git = Git.init().setDirectory(bare.toFile()).setBare(true).call()) {
    }
    Path workspace = tempDir.resolve("workspace");
    var service = new WorkspaceService(
            properties(workspace, null, WorkspaceMode.WORKTREE, bare.toString()), new GitService());

    service.provisionWorktree();

    assertThat(Files.exists(workspace.resolve(".git"))).isTrue();
  }

  @Test
  void worktreeModeKeepsExistingRepository() throws Exception {
    Path workspace = tempDir.resolve("workspace");
    Files.createDirectories(workspace);
    try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
    }
    var service = new WorkspaceService(
            properties(workspace, null, WorkspaceMode.WORKTREE, "file:///nonexistent"),
            new GitService());

    service.provisionWorktree();

    assertThat(Files.exists(workspace.resolve(".git"))).isTrue();
  }

  @Test
  void sharedModeNeverProvisionsAClone() throws Exception {
    Path workspace = tempDir.resolve("workspace");
    var service = new WorkspaceService(
            properties(workspace, null, WorkspaceMode.SHARED, "file:///nonexistent"),
            new GitService());

    service.provisionWorktree();

    assertThat(Files.exists(workspace)).isFalse();
  }

  private static DslBuilderProperties properties(Path workspaceDir, Path sourceDir,
          WorkspaceMode mode) {
    return properties(workspaceDir, sourceDir, mode, null);
  }

  private static DslBuilderProperties properties(Path workspaceDir, Path sourceDir,
          WorkspaceMode mode, String repoUrl) {
    return new DslBuilderProperties(
            workspaceDir,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            sourceDir,
            mode,
            null,
            null,
            new DslBuilderProperties.Git(true, null, null, repoUrl, null, null, 5, false,
                    "origin"),
            null,
            new DslBuilderProperties.Bundles(1, 200),
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null);
  }
}
