package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class DslDefinitionBundleServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DslDefinitionBundleService service = newService(
          DslProperties.bundleServiceDefaults());

  private DslDefinitionBundleService newService(DslProperties props) {
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("dsl/" + name + "Dsl.java"));
    @SuppressWarnings("unchecked")
    ObjectProvider<DslBuilderClient> emptyBuilder = mock(ObjectProvider.class);
    when(emptyBuilder.getIfAvailable()).thenReturn(null);
    return new DslDefinitionBundleService(mapper, Optional.empty(), props, resolver, emptyBuilder);
  }

  @Test
  void exportReadsSourceFiles(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    writeSource(dir, "B", "transaction", "v2");

    DefinitionBundle bundle = service.export(dir, false);

    assertThat(bundle.formatVersion()).isEqualTo(StarterConstants.BUNDLE_FORMAT_VERSION);
    assertThat(bundle.engineVersion()).isNotBlank();
    assertThat(bundle.exportedAt()).isNotBlank();
    assertThat(bundle.definitions()).hasSize(2);
    assertThat(bundle.definitions()).extracting(e -> e.definition().name())
            .containsExactly("A", "B");
    assertThat(bundle.definitions()).extracting(DefinitionBundleEntry::source)
            .containsOnly("source");
  }

  @Test
  void exportWithDraftsLabelsWorkingTreeSource(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    writeSource(dir, "C", "function", "v3");

    DefinitionBundle bundle = service.export(dir, true);

    assertThat(bundle.definitions()).hasSize(2);
    assertThat(bundle.definitions()).extracting(e -> e.definition().name())
            .containsExactly("A", "C");
    assertThat(bundle.definitions()).extracting(DefinitionBundleEntry::source)
            .containsOnly("draft");
  }

  @Test
  void exportReturnsEmptyBundleWhenNoSourceFilesExist(@TempDir Path dir) {
    DefinitionBundle bundle = service.export(dir, true);

    assertThat(bundle.definitions()).isEmpty();
    assertThat(bundle.formatVersion()).isEqualTo(StarterConstants.BUNDLE_FORMAT_VERSION);
  }

  @Test
  void exportSkipsUnparseableFiles(@TempDir Path dir) throws IOException {
    Path dsl = dir.resolve("dsl");
    Files.createDirectories(dsl);
    Files.writeString(dsl.resolve("bad.java"), "not-java", StandardCharsets.UTF_8);
    writeSource(dir, "Good", "process", "v1");

    DefinitionBundle bundle = service.export(dir, false);

    assertThat(bundle.definitions()).hasSize(1);
    assertThat(bundle.definitions().get(0).definition().name()).isEqualTo("Good");
  }

  @Test
  void validateForImportAcceptsValidBundle() {
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);

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
                    new DraftRequest("A", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing or invalid formatVersion");
  }

  @Test
  void validateForImportRejectsUnsupportedFormatVersion() {
    DefinitionBundle bundle = new DefinitionBundle(99, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported bundle formatVersion 99");
  }

  @Test
  void validateForImportRejectsEmptyDefinitions() {
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now", List.of(), null);
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no definitions");
  }

  @Test
  void validateForImportRejectsBlankName() {
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);
    assertThatThrownBy(() -> service.validateForImport(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank name");
  }

  @Test
  void exportSetsDigest(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    writeSource(dir, "B", "transaction", "v2");

    DefinitionBundle bundle = service.export(dir, false);

    assertThat(bundle.digest()).isNotNull();
    assertThat(bundle.digest()).isEqualTo(service.computeDigest(bundle.definitions()));
  }

  @Test
  void computeDigestMatchesGoldenFixture() throws IOException {
    DefinitionBundle bundle = mapper.readValue(
            getClass().getResourceAsStream("/dsl-bundle-golden.json"), DefinitionBundle.class);

    assertThat(bundle.digest()).isNotNull();
    assertThat(service.computeDigest(bundle.definitions())).isEqualTo(bundle.digest());
  }

  @Test
  void verifyDigestAcceptsCorrectBundle(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    DefinitionBundle bundle = service.export(dir, false);

    service.verifyDigest(bundle);
  }

  @Test
  void verifyDigestRejectsTamperedBundle(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    DefinitionBundle bundle = service.export(dir, false);
    DefinitionBundle tampered = new DefinitionBundle(
            bundle.formatVersion(),
            bundle.engineVersion(),
            bundle.exportedAt(),
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v2", "q", null, null),
                    "source")),
            bundle.digest());

    assertThatThrownBy(() -> service.verifyDigest(tampered))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("BUNDLE_DIGEST_MISMATCH");
  }

  @Test
  void verifyDigestAcceptsLegacyNullWhenNotRequired() {
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);

    service.verifyDigest(bundle);
  }

  @Test
  void verifyDigestRejectsLegacyNullWhenRequired(@TempDir Path dir) {
    DslProperties strictProps = DslProperties.builder()
            .sourceDir(dir.toString())
            .bundles(new DslProperties.Bundles(true))
            .build();
    DslDefinitionBundleService strict = newService(strictProps);
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", "Published", "v1", "q", null, null),
                    "source")),
            null);

    assertThatThrownBy(() -> strict.verifyDigest(bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("BUNDLE_DIGEST_MISSING");
  }

  @Test
  void diffForImportClassifiesEntries(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "v1");
    writeSource(dir, "B", "transaction", "v2");
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(
                    new DefinitionBundleEntry(
                            new DraftRequest("A", "process", "Published", "v1", "q",
                                    sourceBody("A", "process", "v1"), null),
                            "source"),
                    new DefinitionBundleEntry(
                            new DraftRequest("B", "transaction", "Published", "v3", "q",
                                    sourceBody("B", "transaction", "v3"), null),
                            "source"),
                    new DefinitionBundleEntry(
                            new DraftRequest("C", "function", "Published", "v1", "q",
                                    sourceBody("C", "function", "v1"), null),
                            "source"),
                    new DefinitionBundleEntry(
                            new DraftRequest("", "process", "Published", "v1", "q", null, null),
                            "source")),
            null);

    List<ImportEntryResult> results = service.diffForImport(dir, bundle);

    assertThat(results).extracting(ImportEntryResult::name)
            .containsExactly("A", "B", "C", "?");
    assertThat(results).extracting(ImportEntryResult::outcome)
            .containsExactly("unchanged", "updated", "created", "skipped");
    assertThat(dir.resolve("dsl/ADsl.java")).exists();
    assertThat(dir.resolve("dsl/BDsl.java")).exists();
    assertThat(dir.resolve("dsl/CDsl.java")).doesNotExist();
  }

  private String sourceBody(String name, String type, String version) {
    return "// version=" + version + "\nDsl." + type + "(\"" + name + "\")\n";
  }

  private void writeSource(Path dir, String name, String type, String version)
          throws IOException {
    Path dsl = dir.resolve("dsl");
    Files.createDirectories(dsl);
    Files.writeString(dsl.resolve(name + "Dsl.java"), sourceBody(name, type, version),
            StandardCharsets.UTF_8);
  }
}
