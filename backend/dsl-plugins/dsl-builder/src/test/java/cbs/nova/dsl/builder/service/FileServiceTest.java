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
            null);
    service = new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L));
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
}
