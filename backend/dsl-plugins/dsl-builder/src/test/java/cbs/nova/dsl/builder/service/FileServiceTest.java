package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.config.WorkspaceMode;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentRequest;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentResponse;
import cbs.nova.dsl.builder.repository.FileRepository;
import com.github.benmanes.caffeine.cache.Ticker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class FileServiceTest {

  @TempDir
  Path sourceDir;

  @TempDir
  Path workspaceDir;

  private FileService service;

  @BeforeEach
  void setUp() {
    var properties = properties(WorkspaceMode.SHARED);
    service = fileService(properties);
  }

  private DslBuilderProperties properties(WorkspaceMode mode) {
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
            new DslBuilderProperties.Files(0, 100, 32, 8, 5L),
            null,
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

  private static FileService fileService(DslBuilderProperties properties) {
    return new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            new WorkspaceService(properties, new GitService()),
            new NoOpGitStatusProvider());
  }

  /**
   * No-op {@link org.springframework.beans.factory.ObjectProvider} stub. Tests that exercise
   * {@link GitStatusService} pass a real service instead. This default returns {@code null} so
   * {@code FileService} skips the invalidation step. Most {@code ObjectProvider} methods have
   * sensible default implementations, so only the {@code *Available()} hooks are overridden.
   */
  private static final class NoOpGitStatusProvider
          implements
            ObjectProvider<GitStatusService> {
    @Override
    public GitStatusService getIfAvailable() {
      return null;
    }

    @Override
    public GitStatusService getIfUnique() {
      return null;
    }
  }

  /**
   * Single-element {@link org.springframework.beans.factory.ObjectProvider} stub for tests that
   * need a real {@link GitStatusService} instance.
   */
  private static final class SingleObjectProvider<T>
          implements
            ObjectProvider<T> {
    private final T value;

    SingleObjectProvider(T value) {
      this.value = value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }
  }

  /**
   * {@link GitStatusService} stub that records {@link GitStatusService#invalidate()} calls. Returns
   * empty {@link java.util.Optional} for {@code status(Path)} so tests stay filesystem- agnostic.
   */
  private static final class RecordingGitStatusService extends GitStatusService {
    private int invalidateCalls = 0;

    RecordingGitStatusService() {
      super(new DslBuilderProperties(
              Path.of(System.getProperty("java.io.tmpdir"), "recording-gitstatus-test"),
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
              null,
              null,
              null,
              null,
              null,
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
              null));
    }

    @Override
    public void invalidate() {
      invalidateCalls++;
    }

    int invalidateCalls() {
      return invalidateCalls;
    }
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<GitStatusService> castingProvider(
          GitStatusService value) {
    return (ObjectProvider<GitStatusService>) (ObjectProvider<?>) new SingleObjectProvider<>(
            value);
  }

  @Test
  void readsFromSharedSourceRoot() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    var response = service.readFile("dsl/LoanDsl.java");

    assertThat(response.content()).isEqualTo("source content");
    assertThat(response.path()).isEqualTo("dsl/LoanDsl.java");
    assertThat(response.pending()).isFalse();
    assertThat(response.crc32()).isEqualTo(FileContentResponse.crc32("source content"));
  }

  @Test
  void sharedModeFlushesWritesIntoSourceDir() throws IOException {
    service.stageWrite("dsl/LoanDsl.java", "draft content");
    service.flushPending();

    assertThat(Files.readString(sourceDir.resolve("dsl/LoanDsl.java"))).isEqualTo("draft content");
    assertThat(Files.exists(workspaceDir.resolve("dsl/LoanDsl.java"))).isFalse();
  }

  @Test
  void worktreeModeFlushesWritesIntoWorkspaceDir() throws IOException {
    var worktreeService = fileService(properties(WorkspaceMode.WORKTREE));

    worktreeService.stageWrite("dsl/LoanDsl.java", "draft content");
    worktreeService.flushPending();

    assertThat(Files.readString(workspaceDir.resolve("dsl/LoanDsl.java")))
            .isEqualTo("draft content");
    assertThat(Files.exists(sourceDir.resolve("dsl/LoanDsl.java"))).isFalse();
  }

  @Test
  void worktreeModeDoesNotFallBackToSourceDirForReads() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");
    var worktreeService = fileService(properties(WorkspaceMode.WORKTREE));

    assertThatThrownBy(() -> worktreeService.readFile("dsl/LoanDsl.java"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("file not found");
  }

  @Test
  void worktreeModeListsFilesFromWorkspaceDir() throws IOException {
    Files.createDirectories(workspaceDir.resolve("dsl"));
    Files.writeString(workspaceDir.resolve("dsl/A.java"), "a");
    var worktreeService = fileService(properties(WorkspaceMode.WORKTREE));

    assertThat(worktreeService.listFiles(null)).extracting(e -> e.path())
            .containsExactly("dsl/A.java");
  }

  @Test
  void readsPendingDraftFromBuffer() throws IOException {
    service.stageWrite("dsl/LoanDsl.java", "draft content");

    var response = service.readFile("dsl/LoanDsl.java");

    assertThat(response.content()).isEqualTo("draft content");
    assertThat(response.crc32()).isEqualTo(FileContentResponse.crc32("draft content"));
    assertThat(response.pending()).isTrue();
  }

  @Test
  void flushedDraftOverridesSourceRead() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    service.stageWrite("dsl/LoanDsl.java", "draft content");
    service.flushPending();

    var response = service.readFile("dsl/LoanDsl.java");
    assertThat(response.content()).isEqualTo("draft content");
    assertThat(response.crc32()).isEqualTo(FileContentResponse.crc32("draft content"));
    assertThat(response.pending()).isFalse();
  }

  @Test
  void throwsWhenFileMissingInRoot() {
    assertThatThrownBy(() -> service.readFile("dsl/MissingDsl.java"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("file not found");
  }

  @Test
  void existsChecksBufferAndRoot() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    assertThat(service.exists("dsl/LoanDsl.java")).isTrue();
    assertThat(service.exists("dsl/MissingDsl.java")).isFalse();

    service.stageWrite("dsl/MissingDsl.java", "draft");
    assertThat(service.exists("dsl/MissingDsl.java")).isTrue();
  }

  @Test
  void listsFilesFromSharedRoot() throws IOException {
    Path source = sourceDir.resolve("dsl").resolve("A.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "a");
    service.stageWrite("models/B.java", "b");
    service.stageWrite("dsl/C.java", "c");
    service.flushPending();

    var entries = service.listFiles(null);

    assertThat(entries).extracting(e -> e.path())
            .containsExactly("dsl/A.java", "dsl/C.java", "models/B.java");
    assertThat(service.listFiles("models")).extracting(e -> e.path())
            .containsExactly("models/B.java");
  }

  @Test
  void stageAllSkipsBlankPathsAndFlushes() throws IOException {
    int staged = service.stageAll(List.of(
            new FileContentRequest("dsl/A.java", "a"),
            new FileContentRequest(" ", "skipped"),
            new FileContentRequest("dsl/B.java", null)));

    assertThat(staged).isEqualTo(2);
    var result = service.flushPending();
    assertThat(result.flushed()).isEqualTo(2);
    assertThat(result.failed()).isZero();
    assertThat(Files.readString(sourceDir.resolve("dsl/B.java"))).isEmpty();
  }

  @Test
  void pendingCountReflectsStagedWrites() {
    assertThat(service.pendingCount()).isZero();
    service.stageWrite("dsl/A.java", "a");
    assertThat(service.pendingCount()).isEqualTo(1);
    service.flushPending();
    assertThat(service.pendingCount()).isZero();
  }

  @Test
  void flushPendingInvalidatesGitStatusWhenAtLeastOneFileLands() throws IOException {
    // Gap G6 / invariant I5: a just-flushed write must show as a draft on the next status read.
    // Use a recording GitStatusService stub so we can assert {@code invalidate()} was called.
    var properties = properties(WorkspaceMode.SHARED);
    var recording = new RecordingGitStatusService();
    var fileService = new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            new WorkspaceService(properties, new GitService()),
            castingProvider(recording));

    fileService.stageWrite("dsl/A.java", "a");
    var result = fileService.flushPending();

    assertThat(result.flushed()).isEqualTo(1);
    assertThat(recording.invalidateCalls()).isEqualTo(1);
  }

  @Test
  void flushPendingSkipsInvalidationWhenBufferIsEmpty() throws IOException {
    var recording = new RecordingGitStatusService();
    var noopProperties = properties(WorkspaceMode.SHARED);
    service = new FileService(noopProperties,
            new FileRepository(),
            new FileBuffer(BuilderServiceConfiguration.pendingCache(noopProperties,
                    Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            new WorkspaceService(noopProperties, new GitService()),
            castingProvider(recording));

    // No staged writes, nothing flushed — invalidate must not run.
    var result = service.flushPending();
    assertThat(result.flushed()).isZero();
    assertThat(recording.invalidateCalls()).isZero();
  }
}
