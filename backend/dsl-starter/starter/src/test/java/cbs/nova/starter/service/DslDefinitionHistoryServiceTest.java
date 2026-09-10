package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.config.properties.DslProperties.Drafts;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DslDefinitionHistoryServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private DslDefinitionHistoryService serviceWithLimit(int limit) {
    DslProperties props = DslProperties.builder()
            .drafts(new Drafts(limit))
            .build();
    return new DslDefinitionHistoryService(props, mapper);
  }

  private DraftRequest sampleRequest(String name, String version) {
    return new DraftRequest(name, "process", "Published", version, "q");
  }

  private void writePublished(Path dir, String name, DraftRequest req) throws Exception {
    Path published = dir.resolve(".workbench/published");
    Files.createDirectories(published);
    Files.writeString(published.resolve(name + ".json"),
            mapper.writeValueAsString(req), StandardCharsets.UTF_8);
  }

  private void writeHistoryEntry(Path dir, String name, String timestamp, String body)
          throws Exception {
    Path historyDir = dir.resolve(".workbench/history").resolve(name);
    Files.createDirectories(historyDir);
    Files.writeString(historyDir.resolve(timestamp + ".json"), body, StandardCharsets.UTF_8);
  }

  // ---- snapshotBeforePublish ----------------------------------------------

  @Test
  void snapshotIsNoOpWhenPublishedMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThatCode(() -> service.snapshotBeforePublish(dir, "Ghost")).doesNotThrowAnyException();
    assertThat(dir.resolve(".workbench/history")).doesNotExist();
  }

  @Test
  void snapshotCopiesPublishedFileByteEqualIntoHistory(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    DraftRequest req = sampleRequest("LoanFlow", "v3");
    writePublished(dir, "LoanFlow", req);

    long before = System.currentTimeMillis();
    service.snapshotBeforePublish(dir, "LoanFlow");
    long after = System.currentTimeMillis();

    Path historyDir = dir.resolve(".workbench/history/LoanFlow");
    assertThat(historyDir).isDirectory();
    List<Path> files;
    try (var stream = Files.list(historyDir)) {
      files = stream.filter(Files::isRegularFile).toList();
    }
    assertThat(files).hasSize(1);
    Path snap = files.get(0);
    String fname = snap.getFileName().toString();
    assertThat(fname).matches("^[0-9]+\\.json$");
    long ts = Long.parseLong(fname.substring(0, fname.length() - ".json".length()));
    assertThat(ts).isBetween(before, after);

    byte[] expected = mapper.writeValueAsBytes(req);
    assertThat(Files.readAllBytes(snap)).isEqualTo(expected);
  }

  @Test
  void snapshotSwallowsFailureWhenSourceUnreadable(@TempDir Path dir) throws Exception {
    // Build a scenario where the published file path is a directory — Files.copy will fail,
    // exercising the warn-swallowed branch without throwing. The history directory is created
    // eagerly before the copy attempt, so it exists but contains no files after the failure.
    DslDefinitionHistoryService service = serviceWithLimit(20);
    Path publishedDir = dir.resolve(".workbench/published");
    Files.createDirectories(publishedDir);
    Files.createDirectories(publishedDir.resolve("Stuck.json"));

    assertThatCode(() -> service.snapshotBeforePublish(dir, "Stuck")).doesNotThrowAnyException();
    Path historyDir = dir.resolve(".workbench/history/Stuck");
    assertThat(historyDir).isDirectory();
    try (var stream = Files.list(historyDir)) {
      assertThat(stream.filter(Files::isRegularFile).toList()).isEmpty();
    }
  }

  // ---- list ---------------------------------------------------------------

  @Test
  void listReturnsEmptyWhenHistoryDirMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThat(service.list(dir, "Anything")).isEmpty();
  }

  @Test
  void listReturnsEntriesNewestFirstWithSizeAndModifiedFields(@TempDir Path dir)
          throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    writeHistoryEntry(dir, "X", "1700000000001", "{\"name\":\"X\",\"type\":\"process\"}");
    writeHistoryEntry(dir, "X", "1700000000300", "{\"name\":\"X\",\"type\":\"transaction\"}");
    writeHistoryEntry(dir, "X", "1700000000200", "{\"name\":\"X\",\"type\":\"helper\"}");

    List<DefinitionHistoryEntry> entries = service.list(dir, "X");

    assertThat(entries).hasSize(3);
    assertThat(entries).extracting(DefinitionHistoryEntry::timestampMillis)
            .containsExactly(1700000000300L, 1700000000200L, 1700000000001L);
    DefinitionHistoryEntry newest = entries.get(0);
    assertThat(newest.timestamp()).isEqualTo("1700000000300");
    assertThat(newest.sizeBytes()).isPositive();
    assertThat(newest.lastModifiedMillis()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void listSkipsNonNumericNamedJsonFile(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    writeHistoryEntry(dir, "X", "1700000000001", "{}");
    Path historyDir = dir.resolve(".workbench/history/X");
    Files.writeString(historyDir.resolve("not-a-number.json"), "{}", StandardCharsets.UTF_8);
    Files.writeString(historyDir.resolve("README.txt"), "noise", StandardCharsets.UTF_8);

    List<DefinitionHistoryEntry> entries = service.list(dir, "X");

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).timestampMillis()).isEqualTo(1700000000001L);
  }

  // ---- readEntry ----------------------------------------------------------

  @Test
  void readEntryReturnsEmptyForBadTimestamps(@TempDir Path dir) {
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThat(service.readEntry(dir, "X", null)).isEmpty();
    assertThat(service.readEntry(dir, "X", "abc")).isEmpty();
    assertThat(service.readEntry(dir, "X", "12.3")).isEmpty();
  }

  @Test
  void readEntryReturnsEmptyWhenFileMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThat(service.readEntry(dir, "X", "1700000000001")).isEmpty();
  }

  @Test
  void readEntryParsesValidSnapshot(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    DraftRequest req = sampleRequest("LoanFlow", "v9");
    writeHistoryEntry(dir, "LoanFlow", "1700000000001", mapper.writeValueAsString(req));

    Optional<DraftRequest> result = service.readEntry(dir, "LoanFlow", "1700000000001");

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(req);
  }

  @Test
  void readEntrySwallowsMalformedJson(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    writeHistoryEntry(dir, "X", "1700000000001", "this is not json");

    assertThat(service.readEntry(dir, "X", "1700000000001")).isEmpty();
  }

  // ---- readPublished ------------------------------------------------------

  @Test
  void readPublishedReturnsEmptyWhenMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThat(service.readPublished(dir, "Missing")).isEmpty();
  }

  @Test
  void readPublishedParsesValidFile(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    DraftRequest req = sampleRequest("LoanFlow", "v1");
    writePublished(dir, "LoanFlow", req);

    Optional<DraftRequest> result = service.readPublished(dir, "LoanFlow");

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(req);
  }

  @Test
  void readPublishedSwallowsMalformedJson(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    Path published = dir.resolve(".workbench/published");
    Files.createDirectories(published);
    Files.writeString(published.resolve("Bad.json"), "{not-json", StandardCharsets.UTF_8);

    assertThat(service.readPublished(dir, "Bad")).isEmpty();
  }

  // ---- prune --------------------------------------------------------------

  @Test
  void pruneKeepsOnlyNewestWhenHistoryLimitExceeded(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(2);
    DraftRequest req = sampleRequest("Prune", "v1");
    writePublished(dir, "Prune", req);

    // Four snapshots — each reads published fresh. We rely on the fact that the published file
    // is byte-stable, so prune (which keeps the 2 newest by filename) leaves timestamps 3 and 4.
    for (int i = 0; i < 4; i++) {
      long before = System.currentTimeMillis();
      service.snapshotBeforePublish(dir, "Prune");
      // Guarantee ascending millis so the filename-string sort matches timestamp order.
      long after = System.currentTimeMillis();
      if (after - before < 2) {
        Thread.sleep(2);
      }
    }

    Path historyDir = dir.resolve(".workbench/history/Prune");
    List<Path> remaining;
    try (var stream = Files.list(historyDir)) {
      remaining = stream.filter(Files::isRegularFile).sorted().toList();
    }
    assertThat(remaining).hasSize(2);
    String newestName = remaining.get(1).getFileName().toString();
    long newestTs = Long.parseLong(newestName.substring(0, newestName.length() - 5));
    assertThat(newestTs).isPositive();
  }

  @Test
  void pruneIsNoOpWhenHistoryLimitIsZeroOrNegative(@TempDir Path dir) throws Exception {
    for (int limit : new int[]{0, -1, -100}) {
      DslDefinitionHistoryService service = serviceWithLimit(limit);
      String name = "Keep_" + limit;
      DraftRequest req = sampleRequest(name, "v" + limit);
      writePublished(dir, name, req);

      for (int i = 0; i < 5; i++) {
        service.snapshotBeforePublish(dir, name);
        Thread.sleep(2);
      }

      Path historyDir = dir.resolve(".workbench/history").resolve(name);
      long count;
      try (var stream = Files.list(historyDir)) {
        count = stream.filter(Files::isRegularFile).count();
      }
      assertThat(count).as("limit=%d", limit).isEqualTo(5);
    }
  }

  // ---- path safety --------------------------------------------------------

  @Test
  void snapshotCollapsesTraversalCharactersInName(@TempDir Path dir) throws Exception {
    // safeFileName("/etc/passwd") = "_etc_passwd". Pre-write the published file under that
    // safe name, then call snapshot with the traversal-prone input. The service must
    // resolve both the published source and the history target via the sanitized name,
    // proving that traversal chars collapse before any Path construction.
    DslDefinitionHistoryService service = serviceWithLimit(20);
    DraftRequest req = sampleRequest("safe-name", "v1");
    String safeName = "_etc_passwd";
    writePublished(dir, safeName, req);

    service.snapshotBeforePublish(dir, "/etc/passwd");

    Path historyRoot = dir.resolve(".workbench/history");
    assertThat(historyRoot).isDirectory();
    Path expected = historyRoot.resolve(safeName);
    assertThat(expected).isDirectory();
    try (var stream = Files.list(expected)) {
      var files = stream.filter(Files::isRegularFile).toList();
      assertThat(files).hasSize(1);
      assertThat(files.get(0).getFileName().toString()).matches("^[0-9]+\\.json$");
    }
    // Nothing under history must resolve outside the root.
    try (var stream = Files.list(historyRoot)) {
      for (Path child : stream.toList()) {
        Path normalized = child.normalize();
        assertThat(normalized.startsWith(historyRoot))
                .as("child %s escaped history root", normalized)
                .isTrue();
      }
    }
  }

  @Test
  void snapshotCollapsesSlashesToUnderscore(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = serviceWithLimit(20);
    DraftRequest req = sampleRequest("LoanFlow", "v1");
    // safeFileName("foo/bar") = "foo_bar"; pre-write the published file under the safe form.
    writePublished(dir, "foo_bar", req);

    service.snapshotBeforePublish(dir, "foo/bar");

    // Slashes collapse to underscores — the history dir is `<safeFileName>` literally,
    // and Files.createDirectories treats underscores as part of the name.
    assertThat(dir.resolve(".workbench/history/foo_bar")).isDirectory();
    assertThat(dir.resolve(".workbench/history/foo/bar")).doesNotExist();
  }

  @Test
  void safeHistoryDirEscapeThrowBranchIsUnreachableFromPublicSurface(@TempDir Path dir)
          throws Exception {
    // The throw in safeHistoryDir fires only when the resolved history dir does not start
    // with historyRoot. safeFileName collapses '/' (but preserves '.') and removes anything
    // outside [A-Za-z0-9._-], so no caller-supplied name can produce a segment that escapes
    // the root. We pin the invariant: after a snapshot with a hostile name, every entry under
    // .workbench/history resolves inside that root.
    DslDefinitionHistoryService service = serviceWithLimit(20);
    // safeFileName("../../etc/passwd") = ".._.._etc_passwd".
    String safeName = ".._.._etc_passwd";
    DraftRequest req = sampleRequest(safeName, "v1");
    writePublished(dir, safeName, req);

    service.snapshotBeforePublish(dir, "../../etc/passwd");

    Path root = dir.resolve(".workbench/history").normalize();
    assertThat(root).isDirectory();
    Path historyDir = root.resolve(safeName);
    assertThat(historyDir).isDirectory();
    try (var stream = Files.list(root)) {
      var children = stream.toList();
      assertThat(children).isNotEmpty();
      for (Path child : children) {
        Path normalized = child.normalize();
        assertThat(normalized.startsWith(root))
                .as("child %s escaped history root", normalized)
                .isTrue();
      }
    }
  }

  @Test
  void listReturnsEmptyForTraversalNameWithoutPublishedFile(@TempDir Path dir) {
    // With no published file under that name, snapshot is a no-op and list has no history dir.
    // The traversal-prone name must be sanitized before being used as a directory segment.
    DslDefinitionHistoryService service = serviceWithLimit(20);

    assertThat(service.list(dir, "../../../etc/passwd")).isEmpty();
  }
}
