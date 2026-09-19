package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class LocalFileTapeSinkTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @TempDir
  Path tempDir;

  @Test
  void writesHeaderEventsAndTrailerToFile() throws IOException {
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), CbsVhsProperties.SinkType.local,
            new CbsVhsProperties.LocalSink(tempDir, 0, 0L));
    LocalFileTapeSink sink = new LocalFileTapeSink(properties, objectMapper,
            () -> Instant.parse("2026-09-19T12:00:00Z"));

    String runId = "run_2v7k9x";
    String correlationId = "corr_a1b2c3";
    sink.start(runId, "Ping", correlationId);
    sink.append(runId, runStartedEvent(correlationId));
    sink.append(runId, callStartEvent(correlationId));
    long eventCount = sink.close(runId);

    assertThat(eventCount).isEqualTo(2L);
    List<String> lines;
    try (Stream<Path> files = Files.list(tempDir)) {
      Path file = files.findFirst().orElseThrow();
      assertThat(file.getFileName().toString())
              .startsWith("20260919-120000_run_2v7k9x_corr_a1b2c3")
              .endsWith(".vhs.jsonl");
      lines = Files.readAllLines(file);
    }

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
  void dropsWritesWhenMaxTapesReached() {
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), CbsVhsProperties.SinkType.local,
            new CbsVhsProperties.LocalSink(tempDir, 1, 0L));
    LocalFileTapeSink sink = new LocalFileTapeSink(properties, objectMapper, Instant::now);

    sink.start("run-1", "Ping", null);
    sink.start("run-2", "Ping", null);
    sink.append("run-2", runStartedEvent(null));

    assertThat(sink.close("run-1")).isEqualTo(0L);
    assertThat(sink.close("run-2")).isEqualTo(0L);
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
}
