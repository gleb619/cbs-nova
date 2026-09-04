package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.repository.DslFileRepository;
import cbs.nova.starter.service.DslFileBulkhead;
import cbs.nova.starter.service.DslFileBuffer;
import cbs.nova.starter.service.DslFileService;
import cbs.nova.starter.service.DefaultDslWorkspaceResolver;
import cbs.nova.starter.service.DslWorkspaceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

class DslFileServiceTest {

  @TempDir
  Path sourceDir;

  private DslFileService service;

  @BeforeEach
  void setUp() {
    DslProperties properties = new DslProperties();
    properties.setSourceDir(sourceDir.toString());
    properties.getFiles().setFlushIntervalSeconds(0);

    String sourceDir = properties.getSourceDir();
    var sourceRoot = Path.of(sourceDir).normalize();
    var workspaceRoot = sourceRoot.resolve(".workbench")
            .resolve("drafts-fs").normalize();

    DslWorkspaceResolver resolver = new DefaultDslWorkspaceResolver(sourceRoot, workspaceRoot);
    DslFileRepository repository = new DslFileRepository();
    DslFileBuffer buffer = new DslFileBuffer();
    DslFileBulkhead bulkhead = new DslFileBulkhead(new Semaphore(1), new Semaphore(1));
    service = new DslFileService(properties, resolver, repository, buffer, bulkhead);
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
  }

  @Test
  void readsFromWorkspaceRootWhenDraftExists() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    service.stageWrite("dsl/LoanDsl.java", "draft content");

    var response = service.readFile("dsl/LoanDsl.java");
    assertThat(response.content()).isEqualTo("draft content");
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
}
