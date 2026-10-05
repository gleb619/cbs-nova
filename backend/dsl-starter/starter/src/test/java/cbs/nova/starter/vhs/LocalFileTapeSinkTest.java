package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import cbs.nova.starter.vhs.replay.VhsTapeReader.VhsTape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class LocalFileTapeSinkTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final VhsTapeReader reader = new VhsTapeReader(objectMapper);

  @TempDir
  Path tempDir;

  @Test
  void writesHeaderEventsAndTrailerToFile() throws IOException {
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(0L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    String runId = "run_2v7k9x";
    String correlationId = "corr_a1b2c3";
    sink.start(runId, "Ping", correlationId);
    sink.append(runId, runStartedEvent(correlationId));
    sink.append(runId, callStartEvent(correlationId));
    long eventCount = sink.close(runId);

    assertThat(eventCount).isEqualTo(2L);
    Path file = singleFile();
    assertThat(file.getFileName().toString())
            .startsWith("20260919-120000_run_2v7k9x_corr_a1b2c3")
            .endsWith(".vhs.jsonl");
    List<String> lines = Files.readAllLines(file);

    assertThat(lines).hasSize(4);
    JsonNode header = objectMapper.readTree(lines.get(0));
    assertThat(header.get("vhs_tape_format_version").asText()).isEqualTo("1.0.0");
    assertThat(header.get("schema_version").asText()).isEqualTo("1");
    assertThat(header.get("source_run_id").asText()).isEqualTo(runId);
    assertThat(header.get("correlation_id").asText()).isEqualTo(correlationId);
    assertThat(header.get("route").asText()).isEqualTo("Ping");

    JsonNode started = objectMapper.readTree(lines.get(1));
    assertThat(started.get("event_type").asText()).isEqualTo("run_started");

    JsonNode closed = objectMapper.readTree(lines.get(3));
    assertThat(closed.get("event_type").asText()).isEqualTo("tape_closed");
    assertThat(closed.get("output").get("event_count").asInt()).isEqualTo(2);
  }

  @Test
  void maxBytesZeroLeavesTapeUnlimited() throws IOException {
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(0L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    String runId = "run-unlimited";
    sink.start(runId, "Ping", null);
    sink.append(runId, runStartedEvent(null));
    sink.append(runId, callStartEvent(null));
    assertThat(sink.close(runId)).isEqualTo(2L);

    VhsTape tape = reader.read(singleFile());
    assertThat(tape.events()).hasSize(2);
  }

  @Test
  void maxBytesTruncatesTapeAndWritesMarker() throws IOException {
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(1L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    String runId = "run-trunc";
    sink.start(runId, "Ping", null);
    sink.append(runId, runStartedEvent(null));
    sink.append(runId, callStartEvent(null));
    assertThat(sink.close(runId)).isEqualTo(0L);

    Path file = singleFile();
    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(3);
    assertThat(objectMapper.readTree(lines.get(1)).get("event_type").asText())
            .isEqualTo("tape_truncated");
    assertThat(objectMapper.readTree(lines.get(2)).get("event_type").asText())
            .isEqualTo("tape_closed");

    VhsTape tape = reader.read(file);
    assertThat(tape.events()).isEmpty();
  }

  @Test
  void maxBytesTruncatesAfterFirstEventAndReplayStillParses() throws IOException {
    // one small event fits, the second exceeds the cap
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(80L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    String runId = "run-partial";
    sink.start(runId, "Ping", null);
    sink.append(runId, runStartedEvent(null));
    sink.append(runId, callStartEvent(null));
    assertThat(sink.close(runId)).isEqualTo(1L);

    Path file = singleFile();
    List<String> lines = Files.readAllLines(file);
    assertThat(lines.get(1)).contains("\"event_type\":\"run_started\"");
    assertThat(objectMapper.readTree(lines.get(lines.size() - 2)).get("event_type").asText())
            .isEqualTo("tape_truncated");

    VhsTape tape = reader.read(file);
    assertThat(tape.events()).hasSize(1);
    assertThat(tape.events().get(0).eventType()).isEqualTo("run_started");
  }

  @Test
  void prunesOldestFinishedTapesAndSparesOpenTapes() throws IOException {
    TestClock clock = clock(Instant.parse("2026-09-19T12:00:00Z"));
    LocalFileTapeSink creator = sink(maxTapes(0), maxBytes(0L), clock);

    createFinishedTape(creator, "run-1");
    clock.tick();
    createFinishedTape(creator, "run-2");
    clock.tick();
    createFinishedTape(creator, "run-3");

    LocalFileTapeSink pruner = sink(maxTapes(2), maxBytes(0L),
            clock(Instant.parse("2026-09-19T12:00:10Z")));
    pruner.start("run-4", "Ping", null);

    assertThat(fileFor("run-1")).isEmpty();
    assertThat(fileFor("run-2")).isPresent();
    assertThat(fileFor("run-3")).isPresent();
    assertThat(fileFor("run-4")).isPresent();

    pruner.close("run-4");
  }

  @Test
  void sanitizesRunIdAndCorrelationIdInFileName() throws IOException {
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(0L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    String runId = "a\\b..c\u0001d";
    String correlationId = "../etc\u007f";
    sink.start(runId, "Ping", correlationId);
    sink.append(runId, runStartedEvent(correlationId));
    sink.close(runId);

    Path file = singleFile();
    assertThat(file.getParent()).isEqualTo(tempDir);
    String name = file.getFileName().toString();
    assertThat(name)
            .startsWith("20260919-120000_a_b__c_d_none")
            .doesNotContain("..")
            .doesNotContain("/")
            .doesNotContain("\\");
  }

  @Test
  void maxTapesZeroDisablesPruning() throws IOException {
    LocalFileTapeSink sink = sink(maxTapes(0), maxBytes(0L),
            clock(Instant.parse("2026-09-19T12:00:00Z")));

    createFinishedTape(sink, "run-a");
    createFinishedTape(sink, "run-b");

    assertThat(fileFor("run-a")).isPresent();
    assertThat(fileFor("run-b")).isPresent();
  }

  private void createFinishedTape(LocalFileTapeSink sink, String runId) {
    sink.start(runId, "Ping", null);
    sink.append(runId, runStartedEvent(null));
    sink.close(runId);
  }

  private Path singleFile() throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files
              .filter(p -> p.getFileName().toString().endsWith(".vhs.jsonl"))
              .findFirst()
              .orElseThrow();
    }
  }

  private Optional<Path> fileFor(String runId) throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files
              .filter(p -> p.getFileName().toString().contains(runId))
              .findFirst();
    }
  }

  private LocalFileTapeSink sink(
          int maxTapes,
          long maxBytes,
          VhsTapeWriter.InstantSource instantSource) {
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), CbsVhsProperties.SinkType.local,
            new CbsVhsProperties.LocalSink(tempDir, maxTapes, maxBytes),
            new CbsVhsProperties.Scrub(false, List.of(), null, null));
    return new LocalFileTapeSink(properties, objectMapper, instantSource);
  }

  private int maxTapes(int value) {
    return value;
  }

  private long maxBytes(long value) {
    return value;
  }

  private TestClock clock(Instant start) {
    return new TestClock(start);
  }

  private TapeEvent runStartedEvent(String correlationId) {
    return new TapeEvent(
            "1", 0, "run_started", "2026-09-19T12:00:00Z", 0L, null, null, null,
            new TapeEvent.Timing("2026-09-19T12:00:00Z", null, null),
            correlationId, Map.of());
  }

  private TapeEvent callStartEvent(String correlationId) {
    return new TapeEvent(
            "1", 1, "call_start", "2026-09-19T12:00:00Z", 0L,
            new TapeEvent.CallMetadata("call_001", "helper", "Ping", "fetch"),
            Map.of(), null,
            new TapeEvent.Timing("2026-09-19T12:00:00Z", null, null),
            correlationId, Map.of());
  }

  static final class TestClock implements VhsTapeWriter.InstantSource {

    private final AtomicReference<Instant> now;

    TestClock(Instant start) {
      this.now = new AtomicReference<>(start);
    }

    void tick() {
      now.updateAndGet(i -> i.plusSeconds(1));
    }

    @Override
    public Instant now() {
      return now.get();
    }
  }
}
