package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.FileEntityKind;
import cbs.nova.starter.service.DslFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class DslFileServiceTest {

  @TempDir
  Path sourceDir;

  private DslFileService service;
  private DslBuilderClient builderClient;

  @BeforeEach
  void setUp() {
    DslProperties properties = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .build();

    builderClient = mock(DslBuilderClient.class);
    service = new DslFileService(properties, builderClient);
  }

  @Test
  void delegatesReadFileToBuilder() {
    when(builderClient.readFile("x.java", FileEntityKind.DSL))
            .thenReturn(new FileContentResponse("x.java", "content", 123L));

    var response = service.readFile("x.java");

    assertThat(response.path()).isEqualTo("x.java");
    assertThat(response.content()).isEqualTo("content");
    assertThat(response.crc32()).isEqualTo(123L);
    verify(builderClient).readFile("x.java", FileEntityKind.DSL);
  }

  @Test
  void delegatesReadModelByNameWithKind() {
    when(builderClient.readFileByName("BatchProcessing", FileEntityKind.MODEL))
            .thenReturn(new FileContentResponse("src/models/BatchModels.java", "model", 1L));

    var response = service.readFileByName("BatchProcessing", FileEntityKind.MODEL);

    assertThat(response.path()).isEqualTo("src/models/BatchModels.java");
    verify(builderClient).readFileByName("BatchProcessing", FileEntityKind.MODEL);
  }

  @Test
  void delegatesStageWriteToBuilderWithKind() {
    service.stageWrite("src/models/FooModels.java", "class Foo {}", FileEntityKind.MODEL);
    verify(builderClient).stageWrite("src/models/FooModels.java", "class Foo {}",
            FileEntityKind.MODEL);
  }

  @Test
  void delegatesFlushDocsToBuilder() {
    service.flushPending(FileEntityKind.DOC);
    verify(builderClient).flushFiles(FileEntityKind.DOC);
  }
}
