package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.DslFileBufferConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.repository.DslFileRepository;
import cbs.nova.starter.service.DslFileBulkhead;
import cbs.nova.starter.service.DslFileBuffer;
import cbs.nova.starter.service.DslFileService;
import cbs.nova.starter.service.DefaultDslWorkspaceResolver;
import cbs.nova.starter.service.DslWorkspaceResolver;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

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
    DslProperties properties = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .files(new DslProperties.Files(null, 0, null, null, null, null))
            .build();

    String sourceDir = properties.sourceDir();
    var sourceRoot = Path.of(sourceDir).normalize();
    var workspaceRoot = sourceRoot.resolve(".workbench")
            .resolve("drafts-fs").normalize();

    DslWorkspaceResolver resolver = new DefaultDslWorkspaceResolver(sourceRoot, workspaceRoot);
    DslFileRepository repository = new DslFileRepository();
    DslFileBuffer buffer = new DslFileBuffer(DslFileBufferConfiguration.pendingCache(properties));
    DslFileBulkhead bulkhead = new DslFileBulkhead(new Semaphore(1), new Semaphore(1), 5L);
    service = new DslFileService(properties, resolver, repository, buffer, bulkhead, null);
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
  void builderReadNotFoundFallsBackToLocalSourceRoot() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    DslBuilderClient builder = builderThrowing(HttpStatus.NOT_FOUND);
    var response = serviceWithBuilder(builder).readFile("dsl/LoanDsl.java");

    assertThat(response.content()).isEqualTo("source content");
    assertThat(response.pending()).isFalse();
  }

  @Test
  void builderReadServerErrorPropagatesWithoutFallback() {
    DslBuilderClient builder = builderThrowing(HttpStatus.INTERNAL_SERVER_ERROR);

    assertThatThrownBy(() -> serviceWithBuilder(builder).readFile("dsl/LoanDsl.java"))
            .isInstanceOf(BuilderApiException.class)
            .extracting(e -> ((BuilderApiException) e).getStatusCode().value())
            .isEqualTo(500);
  }

  @Test
  void builderExistsNotFoundFallsBackToLocalRoots() throws IOException {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "source content");

    DslBuilderClient builder = builderThrowing(HttpStatus.NOT_FOUND);
    var service = serviceWithBuilder(builder);

    assertThat(service.exists("dsl/LoanDsl.java")).isTrue();
    assertThat(service.exists("dsl/MissingDsl.java")).isFalse();
  }

  private DslFileService serviceWithBuilder(DslBuilderClient builder) {
    DslProperties properties = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .files(new DslProperties.Files(null, 0, null, null, null, null))
            .build();
    var sourceRoot = Path.of(properties.sourceDir()).normalize();
    var workspaceRoot = sourceRoot.resolve(".workbench").resolve("drafts-fs").normalize();
    DslWorkspaceResolver resolver = new DefaultDslWorkspaceResolver(sourceRoot, workspaceRoot);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslBuilderClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(builder);
    return new DslFileService(properties, resolver, new DslFileRepository(),
            new DslFileBuffer(DslFileBufferConfiguration.pendingCache(properties)),
            new DslFileBulkhead(new Semaphore(1), new Semaphore(1), 5L), provider);
  }

  private DslBuilderClient builderThrowing(HttpStatus status) {
    DslBuilderClient builder = mock(DslBuilderClient.class);
    var error = new BuilderApiException(status, "TEST", "builder " + status.value());
    when(builder.readFile(anyString())).thenThrow(error);
    when(builder.fileExists(anyString())).thenThrow(error);
    return builder;
  }
}
