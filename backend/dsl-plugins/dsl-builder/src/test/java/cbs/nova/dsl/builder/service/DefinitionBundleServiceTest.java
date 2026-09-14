package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundle;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.dsl.builder.model.VcsModels.DraftRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DefinitionBundleServiceTest {

  @TempDir
  Path workspace;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DefinitionBundleService bundleService;

  @BeforeEach
  void setUp() {
    bundleService = new DefinitionBundleService(properties(null), objectMapper, Optional.empty());
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
  void honorsCustomWorkbenchConfig() throws IOException {
    var custom = new DslBuilderProperties.Workbench(
            "alt/drafts", "alt/published", "alt/history", 7, 200, 50);
    var customService = new DefinitionBundleService(properties(custom), objectMapper,
            Optional.empty());

    // Seed files under the custom paths.
    Path publishedDir = workspace.resolve("alt/published");
    Path draftsDir = workspace.resolve("alt/drafts");
    Files.createDirectories(publishedDir);
    Files.createDirectories(draftsDir);
    objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(publishedDir.resolve("A.json").toFile(),
                    new DraftRequest("A", "process", "Published", "v1", "q"));
    objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(draftsDir.resolve("B.json").toFile(),
                    new DraftRequest("B", "process", "Draft", "v1", "q"));

    DefinitionBundle exported = customService.export(workspace, true);

    assertThat(exported.formatVersion()).isEqualTo(7);
    assertThat(exported.definitions()).extracting(e -> e.definition().name())
            .containsExactlyInAnyOrder("A", "B");

    // Validation rejects bundles whose formatVersion no longer matches the configured one.
    assertThatThrownBy(() -> customService.validateForImport(
            new DefinitionBundle(1, "1.0", "now", List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q"), "published")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported bundle formatVersion 1 (expected 7)");
  }
}
