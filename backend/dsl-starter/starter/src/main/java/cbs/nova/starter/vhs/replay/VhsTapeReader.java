package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.vhs.TapeEvent;
import cbs.nova.starter.vhs.TapeHeader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict reader/validator for VHS tape files produced by {@code VhsTapeWriter} (T555).
 *
 * <p>
 * A tape is fully validated <b>before</b> any of its events are handed to a replay engine:
 *
 * <ul>
 * <li>line 1 must be a {@link TapeHeader} with a supported {@code vhs_tape_format_version} (major
 * version {@value #SUPPORTED_TAPE_FORMAT_MAJOR}) and {@code schema_version}
 * ({@value #SUPPORTED_SCHEMA_VERSION});</li>
 * <li>every event line must parse and carry {@code event_index} values that are strictly
 * monotonically increasing starting at 0 (gaps or out-of-order indexes indicate corruption or
 * truncation);</li>
 * <li>every event's {@code schema_version} must match the header;</li>
 * <li>the last line must be a {@code tape_closed} trailer whose {@code event_count} equals the
 * number of preceding event lines; anything after the trailer is an error.</li>
 * </ul>
 *
 * Any violation raises {@link VhsReplayException} with a specific message — a malformed or
 * truncated tape can never produce a partial replay.
 */
@RequiredArgsConstructor
public final class VhsTapeReader {

  public static final String SUPPORTED_TAPE_FORMAT_MAJOR = "1";
  public static final String SUPPORTED_SCHEMA_VERSION = "1";

  private final @NonNull ObjectMapper objectMapper;

  //TODO: replace ctor with lomboks one
  @Deprecated(forRemoval = true)
  public VhsTapeReader() {
    this(JsonMapper.builder().build());
  }

  /**
   * Read and fully validate a tape file.
   *
   * @param file
   *          path to a {@code .vhs.jsonl} tape
   * @return the validated tape; header plus all events (excluding the {@code tape_closed} trailer)
   * @throws VhsReplayException
   *           on any format violation, with a message describing the exact problem
   */
  public VhsTape read(@NonNull Path file) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read VHS tape: " + file, ex);
    }
    if (lines.isEmpty() || lines.get(0).isBlank()) {
      throw new VhsReplayException("Malformed VHS tape " + file + ": missing header line");
    }

    TapeHeader header = parseHeader(file, lines.get(0));
    List<TapeEvent> events = new ArrayList<>();
    TapeEvent trailer = null;
    int lineNo = 1;
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      lineNo = i + 1;
      if (line.isBlank()) {
        continue;
      }
      if (trailer != null) {
        throw new VhsReplayException(
                "Malformed VHS tape " + file + ": event data found after tape_closed trailer"
                        + " (line " + lineNo + ")");
      }
      TapeEvent event = parseEvent(file, line, lineNo);
      if (!header.schemaVersion().equals(event.schemaVersion())) {
        throw new VhsReplayException(
                "Malformed VHS tape " + file + ": event schema_version '" + event.schemaVersion()
                        + "' does not match header schema_version '" + header.schemaVersion()
                        + "' (line " + lineNo + ")");
      }
      if (event.eventIndex() != events.size()) {
        throw new VhsReplayException(
                "Malformed VHS tape " + file + ": out-of-order or gapped event_index "
                        + event.eventIndex() + ", expected " + events.size()
                        + " (line " + lineNo + "). Tape is truncated or corrupted;"
                        + " refusing to replay partially.");
      }
      if ("tape_closed".equals(event.eventType())) {
        trailer = event;
      } else {
        events.add(event);
      }
    }
    if (trailer == null) {
      throw new VhsReplayException(
              "Malformed VHS tape " + file + ": missing tape_closed trailer."
                      + " Tape was not closed cleanly (truncated?); refusing to replay.");
    }
    long declared = trailerEventCount(trailer);
    if (declared != events.size()) {
      throw new VhsReplayException(
              "Malformed VHS tape " + file + ": tape_closed trailer declares event_count "
                      + declared + " but " + events.size() + " events were found."
                      + " Tape is truncated or corrupted; refusing to replay.");
    }
    return new VhsTape(header, List.copyOf(events));
  }

  private TapeHeader parseHeader(Path file, String line) {
    try {
      TapeHeader header = objectMapper.readValue(line, TapeHeader.class);
      if (header.vhsTapeFormatVersion() == null || header.schemaVersion() == null) {
        throw new VhsReplayException(
                "Malformed VHS tape " + file + ": header is missing vhs_tape_format_version"
                        + " or schema_version");
      }
      String major = header.vhsTapeFormatVersion().split("\\.", 2)[0];
      if (!SUPPORTED_TAPE_FORMAT_MAJOR.equals(major)) {
        throw new VhsReplayException(
                "Unsupported VHS tape format version '" + header.vhsTapeFormatVersion() + "' in "
                        + file + ": only major version " + SUPPORTED_TAPE_FORMAT_MAJOR
                        + " is supported by this replay engine");
      }
      if (!SUPPORTED_SCHEMA_VERSION.equals(header.schemaVersion())) {
        throw new VhsReplayException(
                "Unsupported VHS tape schema_version '" + header.schemaVersion() + "' in " + file
                        + ": only schema_version " + SUPPORTED_SCHEMA_VERSION
                        + " is supported by this replay engine");
      }
      return header;
    } catch (VhsReplayException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new VhsReplayException(
              "Malformed VHS tape " + file + ": header line is not a valid VHS header", ex);
    }
  }

  private TapeEvent parseEvent(Path file, String line, int lineNo) {
    try {
      return objectMapper.readValue(line, TapeEvent.class);
    } catch (Exception ex) {
      throw new VhsReplayException(
              "Malformed VHS tape " + file + ": unparseable event at line " + lineNo, ex);
    }
  }

  private static long trailerEventCount(TapeEvent trailer) {
    // T555's VhsTapeWriter records the trailer count in the event's `output` object
    Object raw = trailer.output() instanceof Map<?, ?> map ? map.get("event_count") : null;
    if (raw instanceof Number number) {
      return number.longValue();
    }
    return -1L;
  }

  /**
   * A fully validated tape: header plus events in original order (trailer excluded).
   */
  public record VhsTape(@NonNull TapeHeader header, @NonNull List<TapeEvent> events) {
  }
}
