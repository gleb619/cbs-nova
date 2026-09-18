package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DslDefinitionHistoryServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private DslDefinitionHistoryService service(int historyLimit) {
    return new DslDefinitionHistoryService(
            DslProperties.builder()
                    .sourceDir("")
                    .drafts(new DslProperties.Drafts(historyLimit))
                    .build(),
            mapper);
  }

  private DraftRequest sampleRequest() {
    return new DraftRequest("order", "process", "Published", "1", null, null, null);
  }

  private Path publishedFile(Path dir, String name) {
    return dir.resolve(".workbench/published").resolve(name + ".json");
  }

  private Path historyDir(Path dir, String name) {
    return dir.resolve(".workbench/history").resolve(name);
  }

  private void writePublished(Path dir, String name, DraftRequest req) throws Exception {
    Path file = publishedFile(dir, name);
    Files.createDirectories(file.getParent());
    mapper.writeValue(file.toFile(), req);
  }

  private void writeHistoryEntry(Path dir, String name, String timestamp, DraftRequest req)
          throws Exception {
    Path file = historyDir(dir, name).resolve(timestamp + ".json");
    Files.createDirectories(file.getParent());
    mapper.writeValue(file.toFile(), req);
  }

  @Test
  void snapshotBeforePublishNoOpsWhenPublishedFileMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = service(20);

    service.snapshotBeforePublish(dir, "order");

    assertThat(dir.resolve(".workbench/history")).doesNotExist();
  }

  @Test
  void snapshotBeforePublishCopiesCurrentPublishedAndLists(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    writePublished(dir, "order", sampleRequest());

    service.snapshotBeforePublish(dir, "order");

    List<DefinitionHistoryEntry> entries = service.list(dir, "order");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).timestamp()).matches("^[0-9]+$");
    assertThat(entries.get(0).sizeBytes()).isPositive();
  }

  @Test
  void listReturnsEmptyWhenHistoryDirAbsent(@TempDir Path dir) {
    DslDefinitionHistoryService service = service(20);

    List<DefinitionHistoryEntry> entries = service.list(dir, "order");

    assertThat(entries).isEmpty();
  }

  @Test
  void listOrdersEntriesNewestFirst(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    DraftRequest req = sampleRequest();
    writeHistoryEntry(dir, "order", "0000000000000", req);
    writeHistoryEntry(dir, "order", "0000000000001", req);

    List<DefinitionHistoryEntry> entries = service.list(dir, "order");

    assertThat(entries)
            .extracting(DefinitionHistoryEntry::timestamp)
            .containsExactly("0000000000001", "0000000000000");
  }

  @Test
  void listSkipsMalformedHistoryFiles(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    DraftRequest req = sampleRequest();
    Path h = historyDir(dir, "order");
    Files.createDirectories(h);
    mapper.writeValue(h.resolve("1234.json").toFile(), req);
    Files.writeString(h.resolve("not-a-number.json"), "{}");
    Files.writeString(h.resolve("1234.txt"), "{}");

    List<DefinitionHistoryEntry> entries = service.list(dir, "order");

    assertThat(entries)
            .extracting(DefinitionHistoryEntry::timestamp)
            .containsExactly("1234");
  }

  @Test
  void readEntryRejectsInvalidTimestampBeforeTouchingFilesystem() {
    DslDefinitionHistoryService service = service(20);

    Optional<DraftRequest> result = service.readEntry(Path.of("/does/not/exist"), "order", "abc");

    assertThat(result).isEmpty();
  }

  @Test
  void readEntryReturnsEmptyWhenFileMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = service(20);

    Optional<DraftRequest> result = service.readEntry(dir, "order", "1234567890123");

    assertThat(result).isEmpty();
  }

  @Test
  void readEntryRoundTripsValidHistoryFile(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    DraftRequest req = sampleRequest();
    writeHistoryEntry(dir, "order", "1234567890123", req);

    Optional<DraftRequest> result = service.readEntry(dir, "order", "1234567890123");

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("order");
    assertThat(result.get().version()).isEqualTo("1");
  }

  @Test
  void readEntryReturnsEmptyWhenFileUnparseable(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    Path h = historyDir(dir, "order");
    Files.createDirectories(h);
    Files.writeString(h.resolve("1234567890123.json"), "not-json");

    Optional<DraftRequest> result = service.readEntry(dir, "order", "1234567890123");

    assertThat(result).isEmpty();
  }

  @Test
  void readPublishedReturnsEmptyWhenMissing(@TempDir Path dir) {
    DslDefinitionHistoryService service = service(20);

    Optional<DraftRequest> result = service.readPublished(dir, "order");

    assertThat(result).isEmpty();
  }

  @Test
  void readPublishedRoundTripsCurrentPublished(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    DraftRequest req = sampleRequest();
    writePublished(dir, "order", req);

    Optional<DraftRequest> result = service.readPublished(dir, "order");

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("order");
  }

  @Test
  void pruneKeepsOnlyHistoryLimitNewestEntries(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(2);
    DraftRequest req = sampleRequest();
    writeHistoryEntry(dir, "order", "0000000000000", req);
    writeHistoryEntry(dir, "order", "0000000000001", req);
    writeHistoryEntry(dir, "order", "0000000000002", req);
    writeHistoryEntry(dir, "order", "0000000000003", req);
    writePublished(dir, "order", req);

    service.snapshotBeforePublish(dir, "order");

    List<DefinitionHistoryEntry> entries = service.list(dir, "order");
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).timestamp()).isNotIn("0000000000000", "0000000000001",
            "0000000000002");
    assertThat(entries.get(1).timestamp()).isEqualTo("0000000000003");
  }

  @Test
  void pruneDisabledWhenLimitIsZeroOrNegative(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(0);
    DraftRequest req = sampleRequest();
    writeHistoryEntry(dir, "order", "0000000000000", req);
    writeHistoryEntry(dir, "order", "0000000000001", req);
    writeHistoryEntry(dir, "order", "0000000000002", req);
    writePublished(dir, "order", req);

    service.snapshotBeforePublish(dir, "order");

    assertThat(service.list(dir, "order")).hasSize(4);
  }

  @Test
  void maliciousNameIsSanitizedAndStaysUnderHistoryRoot(@TempDir Path dir) throws Exception {
    DslDefinitionHistoryService service = service(20);
    DraftRequest req = sampleRequest();
    String malicious = "../escaped";
    String sanitized = safeFileName(malicious);
    writePublished(dir, sanitized, req);

    service.snapshotBeforePublish(dir, malicious);

    assertThat(dir.resolve(".workbench/history").resolve(sanitized)).exists();
    assertThat(service.list(dir, malicious)).hasSize(1);
  }

  @Test
  void safeFileNameStripsTraversalAndSpecialCharacters() throws Exception {
    assertThat(safeFileName("../etc/passwd")).isEqualTo(".._etc_passwd");
    assertThat(safeFileName("a-b.c_1")).isEqualTo("a-b.c_1");
  }

  private String safeFileName(String name) throws Exception {
    Method method = DslDefinitionHistoryService.class.getDeclaredMethod("safeFileName",
            String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, name);
  }
}
