package cbs.nova.starter.vhs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
 * asynchronous stages are safely ordered. When a {@code maxBytes} cap is configured, event writes
 * are truncated once the running byte total would exceed it; a single {@code tape_truncated} marker
 * is written and the tape is closed normally.
 */
@Slf4j
public final class VhsTapeWriter {

  private static final String TAPE_FORMAT_VERSION = "1.0.0";
  private static final String SCHEMA_VERSION = "1";
  private static final String TRUNCATED_MARKER_TYPE = "tape_truncated";

  private final ObjectMapper objectMapper;
  private final BufferedWriter writer;
  private final InstantSource instantSource;
  private final Path file;
  private final String runId;
  private final String correlationId;
  private final String route;
  private final Instant startInstant;
  private final long maxBytes;
  private long recordedEventCount;
  private int nextEventIndex;
  private long eventBytes;
  private boolean truncated;
  private boolean truncatedMarkerWritten;
  private boolean closed;

  /**
   * Opens a writer with an unlimited byte cap (legacy convenience constructor).
   */
  @Deprecated(forRemoval = false)
  public VhsTapeWriter(
          @NonNull Path file,
          @NonNull String runId,
          @Nullable String correlationId,
          @NonNull String route,
          @NonNull Instant startInstant,
          @NonNull ObjectMapper objectMapper,
          @NonNull InstantSource instantSource) {
    this(file, runId, correlationId, route, startInstant, objectMapper, instantSource, 0L);
  }

  public VhsTapeWriter(
          @NonNull Path file,
          @NonNull String runId,
          @Nullable String correlationId,
          @NonNull String route,
          @NonNull Instant startInstant,
          @NonNull ObjectMapper objectMapper,
          @NonNull InstantSource instantSource,
          long maxBytes) {
    this.file = file;
    this.runId = runId;
    this.correlationId = correlationId;
    this.route = route;
    this.startInstant = startInstant;
    this.objectMapper = objectMapper;
    this.instantSource = instantSource;
    this.maxBytes = maxBytes < 0 ? 0L : maxBytes;
    try {
      Files.createDirectories(file.getParent());
      this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
      writeHeader();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to open VHS tape: " + file, ex);
    }
  }

  public synchronized void append(@NonNull TapeEvent event) {
    if (closed) {
      throw new IllegalStateException("Tape already closed for runId=" + runId);
    }
    if (truncated) {
      return;
    }
    try {
      String line = objectMapper.writeValueAsString(event);
      long lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
      if (maxBytes > 0 && eventBytes + lineBytes > maxBytes) {
        truncate(lineBytes);
        return;
      }
      writer.write(line);
      writer.newLine();
      recordedEventCount++;
      nextEventIndex++;
      eventBytes += lineBytes;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to append VHS event for runId=" + runId, ex);
    }
  }

  public synchronized long close() {
    if (closed) {
      return recordedEventCount;
    }
    closed = true;
    if (truncated && !truncatedMarkerWritten) {
      writeTruncatedMarker();
    }
    Instant now = instantSource.now();
    TapeEvent trailer = new TapeEvent(
            SCHEMA_VERSION,
            nextEventIndex,
            "tape_closed",
            format(now),
            startInstant.until(now, ChronoUnit.MILLIS),
            null,
            null,
            Map.of("event_count", recordedEventCount),
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
    return recordedEventCount;
  }

  @NonNull
  public Path file() {
    return file;
  }

  private void truncate(long rejectedLineBytes) {
    truncated = true;
    writeTruncatedMarker();
    log.info(
            "VHS tape truncated for runId={}: maxBytes={} exceeded by next event of {} bytes."
                    + " Recorded {} events.",
            runId, maxBytes, rejectedLineBytes, recordedEventCount);
  }

  private void writeTruncatedMarker() {
    if (truncatedMarkerWritten) {
      return;
    }
    truncatedMarkerWritten = true;
    Instant now = instantSource.now();
    TapeEvent marker = new TapeEvent(
            SCHEMA_VERSION,
            nextEventIndex,
            TRUNCATED_MARKER_TYPE,
            format(now),
            startInstant.until(now, ChronoUnit.MILLIS),
            null,
            null,
            Map.of(
                    "event_count", recordedEventCount,
                    "max_bytes", maxBytes,
                    "truncated_at_bytes", eventBytes),
            new TapeEvent.Timing(null, null, null),
            correlationId,
            Map.of());
    try {
      writer.write(objectMapper.writeValueAsString(marker));
      writer.newLine();
    } catch (IOException ex) {
      log.warn("Failed to write VHS truncated marker for runId={}: {}", runId, ex.getMessage());
    }
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
