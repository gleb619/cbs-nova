package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
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

class FileServiceTest {

  @TempDir
  Path sourceDir;

  @TempDir
  Path workspaceDir;

  private FileService service;

  @BeforeEach
  void setUp() {
    var properties = new DslBuilderProperties(
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
            null,
            null,
            new DslBuilderProperties.Files(0, 100, 32, 8, 5L),
            null,
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
    var gitStatusProvider = new NoOpGitStatusProvider();
    service = new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            gitStatusProvider);
  }

  /**
   * No-op {@link org.springframework.beans.factory.ObjectProvider} stub. Tests that exercise
   * {@link GitStatusService} pass a real service instead. This default returns {@code null} so
   * {@code FileService} skips the invalidation step. Most {@code ObjectProvider} methods have
   * sensible default implementations, so only the {@code *Available()} hooks are overridden.
   */
  private static final class NoOpGitStatusProvider
          implements
            org.springframework.beans.factory.ObjectProvider<GitStatusService> {
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
            org.springframework.beans.factory.ObjectProvider<T> {
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
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
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
  private static org.springframework.beans.factory.ObjectProvider<GitStatusService> castingProvider(
          GitStatusService value) {
    return (org.springframework.beans.factory.ObjectProvider<GitStatusService>) (org.springframework.beans.factory.ObjectProvider<?>) new SingleObjectProvider<>(
            value);
  }

  @Test
  void readsFromSourceRootWhenWorkspaceFileMissing() throws IOException {
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
  void readsFromWorkspaceRootWhenDraftExists() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

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
  void throwsWhenFileMissingInBothRoots() {
    assertThatThrownBy(() -> service.readFile("dsl/MissingDsl.java"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("file not found");
  }

  @Test
  void existsChecksBufferAndBothRoots() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    assertThat(service.exists("dsl/LoanDsl.java")).isTrue();
    assertThat(service.exists("dsl/MissingDsl.java")).isFalse();

    service.stageWrite("dsl/MissingDsl.java", "draft");
    assertThat(service.exists("dsl/MissingDsl.java")).isTrue();
  }

  @Test
  void listsFilesFromWorkspaceRootOnly() throws IOException {
    Path source = sourceDir.resolve("dsl").resolve("A.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "a");
    service.stageWrite("models/B.java", "b");
    service.stageWrite("dsl/C.java", "c");
    service.flushPending();

    var entries = service.listFiles(null);

    assertThat(entries).extracting(e -> e.path())
            .containsExactly("dsl/C.java", "models/B.java");
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
    assertThat(Files.readString(workspaceDir.resolve("dsl/B.java"))).isEmpty();
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
    var properties = new DslBuilderProperties(
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
            null,
            null,
            new DslBuilderProperties.Files(0, 100, 32, 8, 5L),
            null,
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
    var recording = new RecordingGitStatusService();
    @SuppressWarnings("unchecked")
    var provider = (org.springframework.beans.factory.ObjectProvider<GitStatusService>) (org.springframework.beans.factory.ObjectProvider<?>) new SingleObjectProvider<>(
            recording);
    var fileService = new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            provider);

    fileService.stageWrite("dsl/A.java", "a");
    var result = fileService.flushPending();

    assertThat(result.flushed()).isEqualTo(1);
    assertThat(recording.invalidateCalls()).isEqualTo(1);
  }

  @Test
  void flushPendingSkipsInvalidationWhenBufferIsEmpty() throws IOException {
    var recording = new RecordingGitStatusService();
    var noopProperties = new DslBuilderProperties(
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
            null,
            null,
            new DslBuilderProperties.Files(0, 100, 32, 8, 5L),
            null,
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
    service = new FileService(noopProperties,
            new FileRepository(),
            new FileBuffer(BuilderServiceConfiguration.pendingCache(noopProperties,
                    Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L),
            castingProvider(recording));

    // No staged writes, nothing flushed — invalidate must not run.
    var result = service.flushPending();
    assertThat(result.flushed()).isZero();
    assertThat(recording.invalidateCalls()).isZero();
  }
}
