package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

class DslDefinitionBundleServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DslDefinitionBundleService service = new DslDefinitionBundleService(mapper,
          Optional.empty());

  @Test
  void exportReadsPublishedMarkers(@TempDir Path dir) throws IOException {
    writePublished(dir, "A", "process", "v1");
    writePublished(dir, "B", "transaction", "v2");

    DefinitionBundle bundle = service.export(dir, false);

    assertThat(bundle.formatVersion()).isEqualTo(DslDefinitionBundleService.BUNDLE_FORMAT_VERSION);
    assertThat(bundle.engineVersion()).isNotBlank();
    assertThat(bundle.exportedAt()).isNotBlank();
    assertThat(bundle.definitions()).hasSize(2);
    assertThat(bundle.definitions()).extracting(e -> e.definition().name())
            .containsExactly("A", "B");
    assertThat(bundle.definitions()).extracting(DefinitionBundleEntry::source)
            .containsOnly("published");
  }

  @Test
  void exportWithDraftsIncludesOnlyPublishedWhenNameCollides(@TempDir Path dir) throws IOException {
    writePublished(dir, "A", "process", "v1");
    writeDraft(dir, "A", "process", "draft-v1");
    writeDraft(dir, "C", "helper", "v3");

    DefinitionBundle bundle = service.export(dir, true);

    assertThat(bundle.definitions()).hasSize(2);
    assertThat(bundle.definitions()).extracting(e -> e.definition().name())
            .containsExactly("A", "C");
    DefinitionBundleEntry a = bundle.definitions().get(0);
    assertThat(a.source()).isEqualTo("published");
    assertThat(a.definition().version()).isEqualTo("v1");
    assertThat(bundle.definitions().get(1).source()).isEqualTo("draft");
  }

  @Test
  void exportReturnsEmptyBundleWhenNoMarkersExist(@TempDir Path dir) {
    DefinitionBundle bundle = service.export(dir, true);

    assertThat(bundle.definitions()).isEmpty();
    assertThat(bundle.formatVersion()).isEqualTo(DslDefinitionBundleService.BUNDLE_FORMAT_VERSION);
  }

  @Test
  void exportSkipsUnparseableFiles(@TempDir Path dir) throws IOException {
    Path published = dir.resolve(".workbench/published");
    Files.createDirectories(published);
    Files.writeString(published.resolve("bad.json"), "not-json", StandardCharsets.UTF_8);
    writePublished(dir, "Good", "process", "v1");

    DefinitionBundle bundle = service.export(dir, false);

    assertThat(bundle.definitions()).hasSize(1);
    assertThat(bundle.definitions().get(0).definition().name()).isEqualTo("Good");
  }

  @Test
  void validateForImportAcceptsValidBundle() {
    DefinitionBundle bundle = new DefinitionBundle(
            DslDefinitionBundleService.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q"), "published")));

    service.validateForImport(bundle);
  }

  @Test
  void validateForImportRejectsNullBundle() {
    assertThatThrownBy(() -> service.validateForImport(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing or invalid formatVersion");
  }

  @Test
  void validateForImportRejectsZeroFormatVersion() {
    DefinitionBundle bundle = new DefinitionBundle(0, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q"), "published")));
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing or invalid formatVersion");
  }

  @Test
  void validateForImportRejectsUnsupportedFormatVersion() {
    DefinitionBundle bundle = new DefinitionBundle(99, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q"), "published")));
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported bundle formatVersion 99");
  }

  @Test
  void validateForImportRejectsEmptyDefinitions() {
    DefinitionBundle bundle = new DefinitionBundle(
            DslDefinitionBundleService.BUNDLE_FORMAT_VERSION, "1.0", "now", List.of());
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no definitions");
  }

  @Test
  void validateForImportRejectsBlankName() {
    DefinitionBundle bundle = new DefinitionBundle(
            DslDefinitionBundleService.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("", "process", "Published", "v1", "q"), "published")));
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank name");
  }

  private void writePublished(Path dir, String name, String type, String version)
          throws IOException {
    Path d = dir.resolve(".workbench/published");
    Files.createDirectories(d);
    DraftRequest req = new DraftRequest(name, type, "Published", version, "q");
    Files.writeString(d.resolve(name + ".json"), mapper.writeValueAsString(req),
            StandardCharsets.UTF_8);
  }

  private void writeDraft(Path dir, String name, String type, String version) throws IOException {
    Path d = dir.resolve(".workbench/drafts");
    Files.createDirectories(d);
    DraftRequest req = new DraftRequest(name, type, "Draft", version, "q");
    Files.writeString(d.resolve(name + ".json"), mapper.writeValueAsString(req),
            StandardCharsets.UTF_8);
  }

}
