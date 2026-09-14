package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.VcsModels.DraftRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DefinitionHistoryServiceTest {

  @TempDir
  Path workspace;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DefinitionHistoryService historyService;

  @BeforeEach
  void setUp() {
    historyService = new DefinitionHistoryService(properties(null), objectMapper);
  }

  private DslBuilderProperties properties(DslBuilderProperties.Workbench workbench) {
    return new DslBuilderProperties(
            workspace,
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
            workbench,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
  }

  @Test
  void honorsCustomHistoryAndPublishedDirs() throws IOException {
    var custom = new DslBuilderProperties.Workbench(
            ".workbench/drafts", "alt/published", "alt/history", 1, 200, 50);
    var customService = new DefinitionHistoryService(properties(custom), objectMapper);

    // Seed a published file at the custom path so snapshotBeforePublish has something to copy.
    Path publishedDir = workspace.resolve("alt/published");
    Files.createDirectories(publishedDir);
    Path publishedFile = publishedDir.resolve("LoanProcess.json");
    objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(publishedFile.toFile(),
                    new DraftRequest("LoanProcess", "process", "Published", "v1", "q"));

    customService.snapshotBeforePublish(workspace, "LoanProcess");

    Path historyDir = workspace.resolve("alt/history/LoanProcess");
    assertThat(Files.isDirectory(historyDir)).isTrue();
    try (var stream = Files.list(historyDir)) {
      assertThat(stream.filter(Files::isRegularFile).count()).isEqualTo(1);
    }

    assertThat(customService.list(workspace, "LoanProcess")).hasSize(1);
    assertThat(customService.readPublished(workspace, "LoanProcess")).isPresent();
  }
}
