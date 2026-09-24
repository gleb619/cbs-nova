package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.DslFileBufferConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
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

import java.nio.file.Path;
import java.util.concurrent.Semaphore;

class DslFileServiceTest {

  @TempDir
  Path sourceDir;

  private DslFileService service;
  private DslBuilderClient builderClient;

  @BeforeEach
  void setUp() {
    DslProperties properties = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .files(new DslProperties.Files(null, 0, null, null, null, null))
            .build();

    String sourceDir = properties.sourceDir();
    var sourceRoot = Path.of(sourceDir).normalize();
    var workspaceRoot = sourceRoot.resolve(".dsl-workspace").normalize();

    DslWorkspaceResolver resolver = new DefaultDslWorkspaceResolver(sourceRoot, workspaceRoot);
    DslFileRepository repository = new DslFileRepository();
    DslFileBuffer buffer = new DslFileBuffer(DslFileBufferConfiguration.pendingCache(properties));
    DslFileBulkhead bulkhead = new DslFileBulkhead(new Semaphore(1), new Semaphore(1), 5L);
    builderClient = mock(DslBuilderClient.class);
    ObjectProvider<DslBuilderClient> builderProvider = BuilderClientTestSupport
            .providerOf(builderClient);
    service = new DslFileService(properties, resolver, repository, buffer, bulkhead,
            builderProvider);
  }

  @Test
  void delegatesReadFileToBuilder() {
    when(builderClient.readFile("x.java"))
            .thenReturn(new FileContentResponse("x.java", "content", false, 123L));

    var response = service.readFile("x.java");

    assertThat(response.path()).isEqualTo("x.java");
    assertThat(response.content()).isEqualTo("content");
    assertThat(response.pending()).isFalse();
    assertThat(response.crc32()).isEqualTo(123L);
    verify(builderClient).readFile("x.java");
  }

  @Test
  void delegatesStageWriteToBuilder() {
    service.stageWrite("x", "y");
    verify(builderClient).stageWrite("x", "y");

    when(builderClient.pendingCount()).thenReturn(5);
    assertThat(service.pendingCount()).isEqualTo(5);
    verify(builderClient).pendingCount();
  }
}
