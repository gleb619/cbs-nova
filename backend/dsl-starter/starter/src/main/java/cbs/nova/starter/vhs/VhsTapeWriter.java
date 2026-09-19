package cbs.nova.starter.vhs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-run writer that serializes a VHS tape as a single JSON Lines file.
 *
 * <p>
 * The writer is opened with a header line, accepts append-only events, and writes a
 * {@code tape_closed} trailer on close. All operations are synchronized so events arriving from
 * asynchronous stages are safely ordered.
 */
@Slf4j
public final class VhsTapeWriter {

  private static final String TAPE_FORMAT_VERSION = "1.0.0";
  private static final String SCHEMA_VERSION = "1";

  private final ObjectMapper objectMapper;
  private final BufferedWriter writer;
  private final InstantSource instantSource;
  private final String runId;
  private final String correlationId;
  private final String route;
  private final Instant startInstant;
  private long eventCount;
  private boolean closed;

  public VhsTapeWriter(
          @NonNull Path file,
          @NonNull String runId,
          @Nullable String correlationId,
          @NonNull String route,
          @NonNull Instant startInstant,
          @NonNull ObjectMapper objectMapper,
          @NonNull InstantSource instantSource) {
    this.runId = runId;
    this.correlationId = correlationId;
    this.route = route;
    this.startInstant = startInstant;
    this.objectMapper = objectMapper;
    this.instantSource = instantSource;
    try {
      Files.createDirectories(file.getParent());
      this.writer = Files.newBufferedWriter(file);
      writeHeader();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to open VHS tape: " + file, ex);
    }
  }

  public synchronized void append(@NonNull TapeEvent event) {
    if (closed) {
      throw new IllegalStateException("Tape already closed for runId=" + runId);
    }
    try {
      writer.write(objectMapper.writeValueAsString(event));
      writer.newLine();
      eventCount++;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to append VHS event for runId=" + runId, ex);
    }
  }

  public synchronized long close() {
    if (closed) {
      return eventCount;
    }
    closed = true;
    Instant now = instantSource.now();
    TapeEvent trailer = new TapeEvent(
            SCHEMA_VERSION,
            (int) eventCount,
            "tape_closed",
            format(now),
            startInstant.until(now, ChronoUnit.MILLIS),
            null,
            null,
            Map.of("event_count", eventCount),
            new TapeEvent.Timing(null, null, null),
            correlationId,
            Map.of());
    try {
      writer.write(objectMapper.writeValueAsString(trailer));
      writer.newLine();
      writer.flush();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to close VHS tape for runId=" + runId, ex);
    } finally {
      try {
        writer.close();
      } catch (IOException ex) {
        log.warn("Failed to close VHS tape writer for runId={}", runId, ex);
      }
    }
    return eventCount;
  }

  private void writeHeader() throws IOException {
    TapeHeader header = new TapeHeader(
            TAPE_FORMAT_VERSION,
            SCHEMA_VERSION,
            format(startInstant),
            runId,
            correlationId,
            route);
    writer.write(objectMapper.writeValueAsString(header));
    writer.newLine();
  }

  private static String format(Instant instant) {
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }

  /**
   * Source of wall-clock instants, abstracted for testing.
   */
  @FunctionalInterface
  public interface InstantSource {

    @NonNull
    Instant now();
  }
}
