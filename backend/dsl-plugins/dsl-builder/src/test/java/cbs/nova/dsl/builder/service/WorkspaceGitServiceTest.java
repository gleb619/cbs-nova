package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.VcsModels.CommitResult;
import cbs.nova.dsl.builder.model.VcsModels.DiscardResult;
import cbs.nova.dsl.builder.model.VcsModels.LogEntry;
import cbs.nova.dsl.builder.service.GitStatusService.ChangeType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceGitServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void commitModifiedFileLeavesRepoClean() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("README.md"), "v2");

    var service = newService(repo, null);
    RevCommit headBefore = currentHead(repo);

    CommitResult result = service.commit(List.of("README.md"),
            "update readme", "actor", "actor@example.com");

    assertThat(result.commitId()).isNotEqualTo(headBefore.getName());
    assertThat(result.paths()).containsExactly("README.md");
    assertThat(currentHead(repo).getName()).isEqualTo(result.commitId());
    RevCommit author = walkLatest(repo);
    assertThat(author.getAuthorIdent().getName()).isEqualTo("actor");
    assertThat(author.getAuthorIdent().getEmailAddress()).isEqualTo("actor@example.com");
    assertThat(author.getShortMessage()).isEqualTo("update readme");
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void commitAddedAndDeletedFiles() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("added.txt"), "added");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("added.txt").call();
      git.rm().addFilepattern("README.md").call();
    }

    var service = newService(repo, null);

    CommitResult result = service.commit(List.of("added.txt", "README.md"),
            "publish", "actor", "actor@example.com");

    assertThat(result.paths()).containsExactlyInAnyOrder("added.txt", "README.md");
    assertThat(currentChanges(repo)).isEmpty();
    Path readme = repo.resolve("README.md");
    assertThat(Files.exists(readme)).isFalse();
    assertThat(Files.readString(repo.resolve("added.txt"))).isEqualTo("added");
  }

  @Test
  void commitOnlySelectsRequestedPaths() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("wanted.txt"), "wanted");
    Files.writeString(repo.resolve("unrelated.txt"), "unrelated");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern(".").call();
    }

    var service = newService(repo, null);

    service.commit(List.of("wanted.txt"), "just wanted", "actor", "actor@example.com");

    Map<String, ChangeType> changes = currentChanges(repo);
    assertThat(changes).containsOnlyKeys("unrelated.txt");
    assertThat(changes.get("unrelated.txt")).isEqualTo(ChangeType.ADDED);
  }

  @Test
  void commitOfCleanPathRaises409ListingCleanPath() throws Exception {
    Path repo = initRepo();

    var service = newService(repo, null);

    assertThatThrownBy(() -> service.commit(List.of("README.md"),
            "nothing", "actor", "actor@example.com"))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(409);
              assertThat(ex.getCode()).isEqualTo("NOTHING_TO_COMMIT");
              assertThat(ex.getMessage()).contains("README.md");
            });
    // No index mutation should have happened.
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void commitFailureMidwayLeavesIndexUntouched() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("a.txt"), "A");
    Files.writeString(repo.resolve("b.txt"), "B");
    RevCommit headBefore = currentHead(repo);

    var service = newThrowingService(repo, null);

    assertThatThrownBy(() -> service.commit(List.of("a.txt", "b.txt"),
            "boom", "actor", "actor@example.com"))
            .isInstanceOf(BuilderApiException.class)
            .hasMessageContaining("simulated");

    assertThat(currentHead(repo).getName()).isEqualTo(headBefore.getName());
    // After the failed commit the staged entries were reset back to HEAD; for paths not in
    // HEAD (new files) that means they fall out of the index and become untracked again —
    // exactly what the user had on disk before the request. Atomicity holds: nothing got
    // committed, nothing got stuck in a half-staged state.
    Map<String, ChangeType> changes = currentChanges(repo);
    assertThat(changes).containsOnlyKeys("a.txt", "b.txt");
    assertThat(changes.values()).allMatch(c -> c == ChangeType.UNTRACKED);
    try (Git git = Git.open(repo.toFile())) {
      var status = git.status().call();
      assertThat(status.getAdded()).isEmpty();
      assertThat(status.getUntracked()).contains("a.txt", "b.txt");
    }
  }

  @Test
  void discardModifiedFileReturnsToHeadState() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("README.md"), "v2");

    var service = newService(repo, null);

    DiscardResult result = service.discard(List.of("README.md"));

    assertThat(result.discarded()).containsExactly("README.md");
    assertThat(Files.readString(repo.resolve("README.md"))).isEqualTo("v1");
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void discardAddedFileRemovesFileAndIndex() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("new.txt"), "new");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("new.txt").call();
    }

    var service = newService(repo, null);

    DiscardResult result = service.discard(List.of("new.txt"));

    assertThat(result.discarded()).containsExactly("new.txt");
    assertThat(Files.exists(repo.resolve("new.txt"))).isFalse();
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void discardUntrackedFileRemovesFileOnly() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("scratch.txt"), "scratch");

    var service = newService(repo, null);

    DiscardResult result = service.discard(List.of("scratch.txt"));

    assertThat(result.discarded()).containsExactly("scratch.txt");
    assertThat(Files.exists(repo.resolve("scratch.txt"))).isFalse();
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void discardDeletedFileRestoresFromHead() throws Exception {
    Path repo = initRepo();
    Files.delete(repo.resolve("README.md"));

    var service = newService(repo, null);

    DiscardResult result = service.discard(List.of("README.md"));

    assertThat(result.discarded()).containsExactly("README.md");
    assertThat(Files.readString(repo.resolve("README.md"))).isEqualTo("v1");
    assertThat(currentChanges(repo)).isEmpty();
  }

  @Test
  void discardCleanPathIsNoOp() throws Exception {
    Path repo = initRepo();

    var service = newService(repo, null);

    DiscardResult result = service.discard(List.of("README.md"));

    assertThat(result.discarded()).isEmpty();
  }

  @Test
  void logReturnsNewestFirstAndHonoursLimit() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("README.md"), "v2");
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("README.md").call();
      git.commit().setMessage("second").setAuthor("a", "a@example.com")
              .setCommitter("a", "a@example.com").call();
    }

    var service = newService(repo, null);

    List<LogEntry> entries = service.log("README.md", 5);

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).message()).isEqualTo("second");
    assertThat(entries.get(0).author()).isEqualTo("a <a@example.com>");
    assertThat(entries.get(1).message()).isEqualTo("initial");
    assertThat(entries.get(0).timestampMillis())
            .isGreaterThanOrEqualTo(entries.get(1).timestampMillis());
    assertThat(entries.get(0).commitId()).matches("[0-9a-f]{40}");
  }

  @Test
  void showReturnsContentAtOldCommitAndEmptyWhenAbsent() throws Exception {
    Path repo = initRepo();
    RevCommit initial = currentHead(repo);
    Files.writeString(repo.resolve("README.md"), "v2");
    RevCommit second;
    try (Git git = Git.open(repo.toFile())) {
      git.add().addFilepattern("README.md").call();
      second = git.commit().setMessage("v2").setAuthor("a", "a@example.com")
              .setCommitter("a", "a@example.com").call();
    }

    var service = newService(repo, null);

    Optional<String> atInitial = service.show("README.md", initial.getName());
    Optional<String> atSecond = service.show("README.md", second.getName());

    assertThat(atInitial).contains("v1");
    assertThat(atSecond).contains("v2");

    String unknown = "0000000000000000000000000000000000000000";
    Optional<String> absent = service.show("README.md", unknown);
    assertThat(absent).isEmpty();
  }

  @Test
  void rejectsEscapingPaths() throws Exception {
    Path repo = initRepo();

    var service = newService(repo, null);

    assertThatThrownBy(() -> service.commit(List.of("../secret.txt"),
            "x", "a", "a@example.com"))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("INVALID_PATH");
            });
    assertThatThrownBy(() -> service.discard(List.of("../secret.txt")))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("INVALID_PATH");
            });
    assertThatThrownBy(() -> service.log("../secret.txt", 20))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("INVALID_PATH");
            });
    assertThatThrownBy(() -> service.show("../secret.txt", "abc"))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("INVALID_PATH");
            });
  }

  @Test
  void rejectsBlankPaths() throws Exception {
    Path repo = initRepo();

    var service = newService(repo, null);

    assertThatThrownBy(() -> service.commit(List.of(" "),
            "x", "a", "a@example.com"))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("INVALID_PATH");
            });
  }

  @Test
  void translatesPathsWhenWorkspaceIsSubdirectoryOfRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo-" + UUID.randomUUID());
    Path workspace = Files.createDirectories(repoDir.resolve("dsl"));
    try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
      Files.writeString(repoDir.resolve("README.md"), "v1");
      Files.writeString(workspace.resolve("LoanDsl.java"), "class LoanDsl {}");
      git.add().addFilepattern(".").call();
      git.commit().setMessage("initial")
              .setAuthor("dsl-builder-test", "test@example.com")
              .setCommitter("dsl-builder-test", "test@example.com").call();
    }

    // The caller sees workspace-relative paths ("LoanDsl.java"); the service must rewrite
    // them to repo-relative ("dsl/LoanDsl.java") before talking to JGit, and report back in
    // workspace-relative terms so the response stays anchored to the workspace.
    var service = newService(workspace, null);
    Files.writeString(workspace.resolve("LoanDsl.java"), "class LoanDsl { void m() {} }");

    CommitResult result = service.commit(List.of("LoanDsl.java"),
            "edit loan", "actor", "actor@example.com");

    assertThat(result.paths()).containsExactly("LoanDsl.java");
    assertThat(currentChanges(workspace)).isEmpty();
    Map<String, ChangeType> changesAtRepo = currentChanges(repoDir);
    assertThat(changesAtRepo).isEmpty();
  }

  @Test
  void failsWhenGitIsDisabled() throws Exception {
    Path repo = initRepo();
    Files.writeString(repo.resolve("README.md"), "v2");

    var properties = dslBuilderProperties(repo,
            new DslBuilderProperties.Git(false, null, null, null, null, null, 5));
    var status = new GitStatusService(properties, Clock.systemUTC());
    var service = new WorkspaceGitService(properties, () -> status) {
    };

    assertThatThrownBy(() -> service.commit(List.of("README.md"),
            "x", "a", "a@example.com"))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatus().value()).isEqualTo(409);
              assertThat(ex.getCode()).isEqualTo("GIT_NOT_CONFIGURED");
            });
  }

  @Test
  void failsWhenNoRepositoryAvailable() {
    var service = newService(tempDir, null);
    assertThat(service.log("README.md", 20)).isEmpty();
  }

  // --- helpers ---

  private Path initRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo-" + UUID.randomUUID());
    try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
      Files.writeString(repoDir.resolve("README.md"), "v1");
      git.add().addFilepattern(".").call();
      git.commit()
              .setMessage("initial")
              .setAuthor("dsl-builder-test", "test@example.com")
              .setCommitter("dsl-builder-test", "test@example.com")
              .call();
    }
    return repoDir;
  }

  private static WorkspaceGitService newService(Path workspaceDir, String configuredRepoDir) {
    var properties = dslBuilderProperties(workspaceDir,
            new DslBuilderProperties.Git(true, configuredRepoDir,
                    null, null, null, null, 5));
    var status = new GitStatusService(properties, Clock.systemUTC());
    return new WorkspaceGitService(properties, () -> status) {
    };
  }

  private static WorkspaceGitService newThrowingService(Path workspaceDir,
          String configuredRepoDir) {
    var properties = dslBuilderProperties(workspaceDir,
            new DslBuilderProperties.Git(true, configuredRepoDir,
                    null, null, null, null, 5));
    var status = new GitStatusService(properties, Clock.systemUTC());
    return new WorkspaceGitService(properties, () -> status) {
      @Override
      protected RevCommit executeCommit(org.eclipse.jgit.api.CommitCommand command) {
        throw new IllegalStateException("simulated commit failure");
      }
    };
  }

  private static DslBuilderProperties dslBuilderProperties(Path workspaceDir,
          DslBuilderProperties.Git gitProps) {
    return new DslBuilderProperties(
            workspaceDir,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.0-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            null,
            null,
            null,
            null,
            gitProps,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
  }

  private static RevCommit currentHead(Path repoDir) throws Exception {
    try (Repository repository = new FileRepositoryBuilder()
            .findGitDir(repoDir.toFile())
            .build();
            Git git = new Git(repository)) {
      return git.log().call().iterator().next();
    }
  }

  private static RevCommit walkLatest(Path repoDir) throws Exception {
    return currentHead(repoDir);
  }

  private static Map<String, ChangeType> currentChanges(Path repoDir) throws Exception {
    Map<String, ChangeType> changes = new LinkedHashMap<>();
    try (Repository repository = new FileRepositoryBuilder()
            .findGitDir(repoDir.toFile())
            .build();
            Git git = new Git(repository)) {
      var status = git.status().call();
      for (String p : status.getAdded()) {
        changes.put(p, ChangeType.ADDED);
      }
      for (String p : status.getRemoved()) {
        changes.put(p, ChangeType.DELETED);
      }
      for (String p : status.getChanged()) {
        changes.put(p, ChangeType.MODIFIED);
      }
      for (String p : status.getModified()) {
        changes.put(p, ChangeType.MODIFIED);
      }
      for (String p : status.getUntracked()) {
        changes.put(p, ChangeType.UNTRACKED);
      }
      for (String p : status.getMissing()) {
        changes.put(p, ChangeType.DELETED);
      }
      for (String p : status.getConflicting()) {
        changes.put(p, ChangeType.CONFLICTING);
      }
    }
    return changes;
  }
}
