package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundle;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.dsl.builder.model.VcsModels.DraftRequest;
import cbs.nova.dsl.builder.model.VcsModels.DraftResponse;
import cbs.nova.dsl.builder.model.VcsModels.DraftSummary;
import cbs.nova.dsl.builder.model.VcsModels.HistoryDiffResponse;
import cbs.nova.dsl.builder.model.VcsModels.ImportBundleResult;
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

class DraftServiceTest {

  @TempDir
  Path workspace;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DraftService draftService;
  private DefinitionHistoryService historyService;

  @BeforeEach
  void setUp() {
    var properties = properties();
    historyService = new DefinitionHistoryService(properties, objectMapper);
    var bundleService = new DefinitionBundleService(objectMapper, Optional.empty());
    draftService = new DraftService(properties, objectMapper, historyService, bundleService);
  }

  private DslBuilderProperties properties() {
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
  void saveWritesDraftMarkerWithoutReloading() throws IOException {
    DraftResponse response = draftService.save("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v1", "q"));

    assertThat(response.status()).isEqualTo("Draft");
    assertThat(response.reloaded()).isFalse();
    assertThat(response.reloadError()).isNull();
    assertThat(response.loadResult().total()).isZero();
    assertThat(Files.exists(workspace.resolve(".workbench/drafts/LoanProcess.json"))).isTrue();
  }

  @Test
  void saveRejectsBlankName() {
    assertThatThrownBy(() -> draftService.save("LoanProcess",
            new DraftRequest(" ", "process", "Draft", "v1", "q")))
            .isInstanceOf(BuilderApiException.class)
            .hasMessageContaining("name is required");
  }

  @Test
  void publishWritesPublishedMarkerSnapshotsHistoryAndDeletesDraft() throws IOException {
    draftService.save("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v1", "q"));

    DraftResponse response = draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v2", "q"));

    assertThat(response.status()).isEqualTo("Published");
    assertThat(response.reloaded()).isFalse();
    assertThat(Files.exists(workspace.resolve(".workbench/published/LoanProcess.json")))
            .isTrue();
    assertThat(Files.exists(workspace.resolve(".workbench/drafts/LoanProcess.json"))).isFalse();
    assertThat(draftService.history("LoanProcess")).isEmpty();

    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v3", "q"));
    assertThat(draftService.history("LoanProcess")).hasSize(1);
  }

  @Test
  void historyEntryAndReadReturnStoredPayloads() throws IOException {
    draftService.save("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v0", "q"));

    DraftRequest read = draftService.read("LoanProcess");
    assertThat(read.status()).isEqualTo("Draft");
    assertThat(read.version()).isEqualTo("v0");

    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v1", "q"));
    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v2", "q"));
    String timestamp = draftService.history("LoanProcess").get(0).timestamp();
    DraftRequest entry = draftService.historyEntry("LoanProcess", timestamp);
    assertThat(entry.version()).isEqualTo("v1");
  }

  @Test
  void missingHistoryEntryAndDraftThrowNotFound() {
    assertThatThrownBy(() -> draftService.historyEntry("Missing", "123"))
            .isInstanceOf(BuilderApiException.class)
            .hasMessageContaining("No publish history entry");
    assertThatThrownBy(() -> draftService.read("Missing"))
            .isInstanceOf(BuilderApiException.class)
            .hasMessageContaining("Draft not found");
  }

  @Test
  void historyDiffComparesEntryAgainstPublished() throws IOException {
    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v1", "q"));
    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v2", "q"));

    String timestamp = draftService.history("LoanProcess").get(0).timestamp();
    HistoryDiffResponse diff = draftService.historyDiff("LoanProcess", timestamp);

    assertThat(diff.before()).contains("v2");
    assertThat(diff.after()).contains("v1");
    assertThat(diff.hunks()).isNotEmpty();
    assertThat(diff.truncated()).isFalse();
  }

  @Test
  void restoreRepublishesHistoricalEntry() throws IOException {
    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v1", "q"));
    draftService.publish("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Published", "v2", "q"));
    String timestamp = draftService.history("LoanProcess").get(0).timestamp();

    DraftResponse restored = draftService.restore("LoanProcess", timestamp);

    assertThat(restored.status()).isEqualTo("Published");
    assertThat(restored.reloaded()).isFalse();
    assertThatThrownBy(() -> draftService.read("LoanProcess"))
            .isInstanceOf(BuilderApiException.class);
    DraftRequest published = historyService.readPublished(workspace, "LoanProcess").orElseThrow();
    assertThat(published.version()).isEqualTo("v1");
  }

  @Test
  void deleteRemovesDraftMarker() throws IOException {
    draftService.save("LoanProcess",
            new DraftRequest("LoanProcess", "process", "Draft", "v1", "q"));

    DraftResponse deleted = draftService.delete("LoanProcess");

    assertThat(deleted.status()).isEqualTo("Deleted");
    assertThatThrownBy(() -> draftService.read("LoanProcess"))
            .isInstanceOf(BuilderApiException.class);
  }

  @Test
  void listReturnsPagedSummaries() throws IOException {
    draftService.save("A", new DraftRequest("A", "process", "Draft", "v1", "q"));
    draftService.save("B", new DraftRequest("B", "transaction", "Draft", "v1", "q"));

    var page = draftService.list(50, 0);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(DraftSummary::name).containsExactly("A", "B");
  }

  @Test
  void exportBundleIncludesPublishedMarkers() throws IOException {
    draftService.publish("A", new DraftRequest("A", "process", "Published", "v1", "q"));

    DefinitionBundle bundle = draftService.exportBundle(false);

    assertThat(bundle.formatVersion()).isEqualTo(DefinitionBundleService.BUNDLE_FORMAT_VERSION);
    assertThat(bundle.definitions()).extracting(e -> e.definition().name()).containsExactly("A");
    assertThat(bundle.definitions()).extracting(DefinitionBundleEntry::source)
            .containsOnly("published");
  }

  @Test
  void importBundlePublishesEntriesWithoutReloading() throws IOException {
    DefinitionBundle bundle = new DefinitionBundle(
            DefinitionBundleService.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Draft", "v1", "q"), "published")));

    ImportBundleResult result = draftService.importBundle(bundle, false);

    assertThat(result.dryRun()).isFalse();
    assertThat(result.reloaded()).isFalse();
    assertThat(result.published()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(result.results()).extracting(r -> r.outcome()).containsOnly("published");
    assertThat(Files.exists(workspace.resolve(".workbench/published/A.json"))).isTrue();
  }

  @Test
  void importBundleDryRunSkipsWrites() throws IOException {
    DefinitionBundle bundle = new DefinitionBundle(
            DefinitionBundleService.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Draft", "v1", "q"), "published")));

    ImportBundleResult result = draftService.importBundle(bundle, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.results()).extracting(r -> r.outcome()).containsOnly("skipped");
    assertThat(Files.exists(workspace.resolve(".workbench/published/A.json"))).isFalse();
  }

  @Test
  void importBundleRejectsInvalidBundle() {
    assertThatThrownBy(() -> draftService.importBundle(
            new DefinitionBundle(0, "1.0", "now", List.of()), false))
            .isInstanceOf(IllegalArgumentException.class);
  }
}
