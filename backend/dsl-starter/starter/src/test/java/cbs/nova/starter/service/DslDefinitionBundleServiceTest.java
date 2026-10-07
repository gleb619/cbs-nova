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
import cbs.nova.starter.repository.DslFileRepository;
import cbs.nova.starter.service.DslDefinitionBundleService.BundleApplyResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
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
    return new DslDefinitionBundleService(mapper, Optional.empty(), props, resolver, emptyBuilder,
            new DslFileRepository());
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

  @Test
  void applyPublishesAllEntriesAtomically(@TempDir Path dir) throws IOException {
    writeSource(dir, "A", "process", "old-a");
    writeSource(dir, "B", "transaction", "old-b");
    DefinitionBundle bundle = applyBundle(Map.of(
            "A", sourceBody("A", "process", "new-a"),
            "B", sourceBody("B", "transaction", "new-b")));

    BundleApplyResult applied = service.applyToTarget(dir, bundle);

    assertThat(applied.rolledBack()).isFalse();
    assertThat(applied.results()).extracting(ImportEntryResult::outcome)
            .containsExactly("published", "published");
    assertThat(Files.readString(dir.resolve("dsl/ADsl.java")))
            .isEqualTo(sourceBody("A", "process", "new-a"));
    assertThat(Files.readString(dir.resolve("dsl/BDsl.java")))
            .isEqualTo(sourceBody("B", "transaction", "new-b"));
    assertThat(listTmpResidue(dir)).isEmpty();
  }

  @Test
  void applyStagingFailureLeavesTargetByteIdenticalWithoutTmpResidue(@TempDir Path dir)
          throws IOException {
    writeSource(dir, "A", "process", "old-a");
    writeSource(dir, "B", "transaction", "old-b");
    String oldAContent = Files.readString(dir.resolve("dsl/ADsl.java"));
    String oldBContent = Files.readString(dir.resolve("dsl/BDsl.java"));
    // read-only dsl/ dir makes temp-file staging fail for every entry (phase 1)
    Files.setPosixFilePermissions(dir.resolve("dsl"),
            PosixFilePermissions.fromString("r-xr-xr-x"));

    try {
      BundleApplyResult applied = service.applyToTarget(dir,
              applyBundle(Map.of(
                      "A", sourceBody("A", "process", "new-a"),
                      "B", sourceBody("B", "transaction", "new-b"))));

      assertThat(applied.rolledBack()).isTrue();
      assertThat(applied.results()).extracting(ImportEntryResult::outcome)
              .containsExactly("failed", "skipped");
      assertThat(applied.results().get(1).message()).contains("rolled back");
      assertThat(Files.readString(dir.resolve("dsl/ADsl.java"))).isEqualTo(oldAContent);
      assertThat(Files.readString(dir.resolve("dsl/BDsl.java"))).isEqualTo(oldBContent);
    } finally {
      Files.setPosixFilePermissions(dir.resolve("dsl"),
              PosixFilePermissions.fromString("rwxr-xr-x"));
    }
    assertThat(listTmpResidue(dir)).isEmpty();
  }

  @Test
  void applyLaterStagingFailureReportsEarlierStagedEntriesAsRolledBackNotNull(@TempDir Path dir)
          throws IOException {
    writeSource(dir, "A", "process", "old-a");
    writeSource(dir, "B", "transaction", "old-b");
    String oldAContent = Files.readString(dir.resolve("dsl/ADsl.java"));
    Path bFile = dir.resolve("dsl/BDsl.java");
    // unreadable pre-image: A stages fine, B's phase-1 pre-image read throws
    Files.setPosixFilePermissions(bFile, PosixFilePermissions.fromString("---------"));
    try {
      BundleApplyResult applied = service.applyToTarget(dir,
              applyBundle(Map.of(
                      "A", sourceBody("A", "process", "new-a"),
                      "B", sourceBody("B", "transaction", "new-b"))));

      assertThat(applied.rolledBack()).isTrue();
      assertThat(applied.results()).doesNotContainNull();
      assertThat(applied.results()).extracting(ImportEntryResult::outcome)
              .containsExactly("skipped", "failed");
      assertThat(applied.results().get(0).message()).contains("rolled back");
      assertThat(Files.readString(dir.resolve("dsl/ADsl.java"))).isEqualTo(oldAContent);
    } finally {
      Files.setPosixFilePermissions(bFile, PosixFilePermissions.fromString("rw-r--r--"));
    }
    assertThat(listTmpResidue(dir)).isEmpty();
  }

  @Test
  void applyMoveFailureRestoresPreImagesWithoutTmpResidue(@TempDir Path dir)
          throws IOException {
    writeSource(dir, "A", "process", "old-a");
    String oldAContent = Files.readString(dir.resolve("dsl/ADsl.java"));
    // B's target path is a non-empty directory: staging succeeds, the phase-2 atomic
    // move onto it fails with IOException, forcing the pre-image restore path.
    Path bTarget = dir.resolve("dsl/BDsl.java");
    Files.createDirectories(bTarget.resolve("nested"));
    Files.writeString(bTarget.resolve("nested/keep.txt"), "x", StandardCharsets.UTF_8);

    BundleApplyResult applied = service.applyToTarget(dir,
            applyBundle(Map.of(
                    "A", sourceBody("A", "process", "new-a"),
                    "B", sourceBody("B", "transaction", "new-b"))));

    assertThat(applied.rolledBack()).isTrue();
    assertThat(applied.results()).extracting(ImportEntryResult::outcome)
            .containsExactly("skipped", "failed");
    assertThat(applied.results().get(0).message()).contains("rolled back");
    assertThat(applied.results().get(1).outcome()).isEqualTo("failed");
    assertThat(Files.readString(dir.resolve("dsl/ADsl.java"))).isEqualTo(oldAContent);
    assertThat(Files.isDirectory(bTarget)).isTrue();
    assertThat(listTmpResidue(dir)).isEmpty();
  }

  @Test
  void applyAndVerifyRestoresPreImagesWhenDigestMismatch(@TempDir Path dir)
          throws IOException {
    writeSource(dir, "A", "process", "old-a");
    String oldAContent = Files.readString(dir.resolve("dsl/ADsl.java"));
    // duplicate-name entries pass validation but re-export dedups, so the post-apply
    // digest cannot match — the digest check must roll the target back.
    DefinitionBundle bundle = new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            List.of(
                    new DefinitionBundleEntry(
                            new DraftRequest("A", "process", "Published", "v1", "q",
                                    sourceBody("A", "process", "new-a"), null),
                            "source"),
                    new DefinitionBundleEntry(
                            new DraftRequest("A", "process", "Published", "v2", "q",
                                    sourceBody("A", "process", "newer-a"), null),
                            "source")),
            null);

    assertThatThrownBy(() -> service.applyAndVerify(dir, bundle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("BUNDLE_DIGEST_MISMATCH");
    assertThat(Files.readString(dir.resolve("dsl/ADsl.java"))).isEqualTo(oldAContent);
  }

  @Test
  void concurrentReaderNeverObservesPartialFile(@TempDir Path dir) throws Exception {
    writeSource(dir, "A", "process", "old-a");
    String oldContent = Files.readString(dir.resolve("dsl/ADsl.java"));
    StringBuilder big = new StringBuilder(sourceBody("A", "process", "new-a"));
    for (int i = 0; i < 20_000; i++) {
      big.append("// padding ").append(i).append('\n');
    }
    String newContent = big.toString();
    Path target = dir.resolve("dsl/ADsl.java");
    AtomicBoolean applied = new AtomicBoolean();
    AtomicBoolean sawNew = new AtomicBoolean();
    List<String> violations = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    Thread reader = new Thread(() -> {
      while (!applied.get() || !sawNew.get()) {
        try {
          String content = Files.readString(target, StandardCharsets.UTF_8);
          if (!content.equals(oldContent) && !content.equals(newContent)) {
            violations.add("partial content (" + content.length() + " bytes)");
            return;
          }
          if (content.equals(newContent)) {
            sawNew.set(true);
          }
        } catch (Exception e) {
          violations.add("read failed: " + e);
          return;
        }
      }
    });
    reader.setDaemon(true);
    reader.start();

    BundleApplyResult result = service.applyToTarget(dir, applyBundle(Map.of("A", newContent)));
    applied.set(true);
    reader.join(10_000);

    assertThat(result.rolledBack()).isFalse();
    assertThat(violations).isEmpty();
    assertThat(Files.readString(target)).isEqualTo(newContent);
  }

  private List<Path> listTmpResidue(Path dir) throws IOException {
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
    }
  }

  private DefinitionBundle applyBundle(Map<String, String> sources) {
    return new DefinitionBundle(
            StarterConstants.BUNDLE_FORMAT_VERSION, "1.0", "now",
            sources.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new DefinitionBundleEntry(
                            new DraftRequest(e.getKey(), "process", "Published", "v1", "q",
                                    e.getValue(), null),
                            "source"))
                    .toList(),
            null);
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
