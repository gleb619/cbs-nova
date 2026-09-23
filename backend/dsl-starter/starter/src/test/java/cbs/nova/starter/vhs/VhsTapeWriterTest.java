package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.vhs.replay.VhsReplayException;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import cbs.nova.starter.vhs.replay.VhsTapeReader.VhsTape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Characterization specs for {@link VhsTapeWriter} format edge cases (T621).
 *
 * <p>
 * These tests pin current behavior — unicode payloads, embedded CR/LF/CRLF and quotes/backslashes
 * in fields, empty event streams, and truncated tapes — as a cross-version contract together with
 * {@code docs/vhs-tape-format.md}. No writer behavior is prescribed here beyond what exists today.
 */
class VhsTapeWriterTest {

  private static final Instant START = Instant.parse("2026-09-19T12:00:00Z");
  private static final Instant END = Instant.parse("2026-09-19T12:00:01.500Z");

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final VhsTapeReader reader = new VhsTapeReader(objectMapper);

  @TempDir
  Path tempDir;

  @Test
  void roundTripsUnicodeAstralPlaneAndControlCharacters() throws IOException {
    Path file = tempDir.resolve("unicode.vhs.jsonl");
    TapeEvent event = new TapeEvent(
            "1", 0, "trace", "2026-09-19T12:00:00Z", 0L,
            null,
            Map.of(
                    "greeting", "héllo wörld — 你好 — مرحبا",
                    "astral", "emoji 😀 𝄞 𝓏",
                    "mixed", "line1\r\nline2\rline3\nline4"),
            "quote \"backslash \\ end",
            new TapeEvent.Timing("2026-09-19T12:00:00Z", null, null),
            "corr_unicode", Map.of("note", "CRLF\r\ninside metadata"));

    VhsTapeWriter writer = writer(file, "run_unicode", "corr_unicode");
    writer.append(event);
    long count = writer.close();

    assertThat(count).isEqualTo(1L);
    VhsTape tape = reader.read(file);
    assertThat(tape.header().sourceRunId()).isEqualTo("run_unicode");
    assertThat(tape.events()).hasSize(1);
    TapeEvent read = tape.events().get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> input = (Map<String, Object>) read.input();
    assertThat(input.get("greeting")).isEqualTo("héllo wörld — 你好 — مرحبا");
    assertThat(input.get("astral")).isEqualTo("emoji 😀 𝄞 𝓏");
    assertThat(input.get("mixed")).isEqualTo("line1\r\nline2\rline3\nline4");
    assertThat(read.output()).isEqualTo("quote \"backslash \\ end");
    assertThat(read.metadata().get("note")).isEqualTo("CRLF\r\ninside metadata");
  }

  @Test
  void escapesNewlinesAndQuotesOnTheWire() throws IOException {
    Path file = tempDir.resolve("escaped.vhs.jsonl");
    TapeEvent event = new TapeEvent(
            "1", 0, "trace", "2026-09-19T12:00:00Z", 0L,
            null,
            "a\r\nb\rc\nd \"q\" \\",
            null,
            new TapeEvent.Timing(null, null, null),
            null, Map.of());

    VhsTapeWriter escWriter = writer(file, "run_esc", null);
    escWriter.append(event);
    escWriter.close();

    List<String> lines = Files.readAllLines(file);
    // one JSON object per line: no raw control chars or unescaped quotes leak into framing
    assertThat(lines).hasSize(3);
    String wire = lines.get(1);
    assertThat(wire).contains("a\\r\\nb\\rc\\nd \\\"q\\\" \\\\");
    assertThat(wire).doesNotContain("\r").doesNotContain("\n");
  }

  @Test
  void emptyEventStreamProducesValidHeaderOnlyTape() throws IOException {
    Path file = tempDir.resolve("empty.vhs.jsonl");

    VhsTapeWriter emptyWriter = writer(file, "run_empty", "corr_empty");
    long count = emptyWriter.close();

    assertThat(count).isEqualTo(0L);
    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).contains("\"source_run_id\":\"run_empty\"");
    assertThat(lines.get(1)).contains("\"event_type\":\"tape_closed\"");

    VhsTape tape = reader.read(file);
    assertThat(tape.events()).isEmpty();
    // trailer count pinned via raw JSON: exactly zero events declared
    assertThat(objectMapper.readTree(lines.get(1)).get("output").get("event_count").asInt())
            .isZero();
  }

  @Test
  void rejectsAppendAfterCloseButCloseIsIdempotent() throws IOException {
    Path file = tempDir.resolve("closed.vhs.jsonl");
    TapeEvent event = new TapeEvent(
            "1", 0, "trace", "2026-09-19T12:00:00Z", 0L, null, null, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());

    VhsTapeWriter writer = writer(file, "run_closed", null);
    writer.append(event);
    writer.close();

    assertThatThrownBy(() -> writer.append(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Tape already closed");
    assertThat(writer.close()).isEqualTo(1L);
    assertThat(reader.read(file).events()).hasSize(1);
  }

  @Test
  void truncatedTapeMissingTrailerIsRejected() throws IOException {
    Path file = tempDir.resolve("truncated.vhs.jsonl");
    TapeEvent event = new TapeEvent(
            "1", 0, "trace", "2026-09-19T12:00:00Z", 0L, null, null, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());

    VhsTapeWriter truncWriter = writer(file, "run_trunc", null);
    truncWriter.append(event);
    truncWriter.close();

    // simulate crash: keep header + first event, drop tape_closed trailer
    List<String> lines = Files.readAllLines(file);
    Files.write(file, lines.subList(0, 2));

    assertThatThrownBy(() -> reader.read(file))
            .isInstanceOf(VhsReplayException.class)
            .hasMessageContaining("missing tape_closed trailer");
  }

  @Test
  void truncatedTapeMidEventIsRejected() throws IOException {
    Path file = tempDir.resolve("midline.vhs.jsonl");
    TapeEvent event = new TapeEvent(
            "1", 0, "trace", "2026-09-19T12:00:00Z", 0L, null, null, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());

    VhsTapeWriter midWriter = writer(file, "run_mid", null);
    midWriter.append(event);
    midWriter.close();

    // simulate crash mid-write: header + first event + half of an event line
    List<String> lines = Files.readAllLines(file);
    String halfEvent = lines.get(1).substring(0, lines.get(1).length() / 2);
    Files.write(file, List.of(lines.get(0), lines.get(1), halfEvent));

    assertThatThrownBy(() -> reader.read(file))
            .isInstanceOf(VhsReplayException.class)
            .hasMessageContaining("unparseable event at line 3");
  }

  private VhsTapeWriter writer(Path file, String runId, String correlationId) {
    return new VhsTapeWriter(
            file, runId, correlationId, "Ping", START, objectMapper,
            () -> END);
  }
}
