package cbs.nova.starter.vhs;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local file-system implementation of {@link VhsTapeSink}.
 *
 * <p>
 * Writes one tape file per run under {@code cbs.vhs.sink.local.path}. Files are named
 * {@code <recorded_at>_<runId>_<correlationId>.vhs.jsonl}. {@code max-tapes} prunes the oldest
 * finished files when the directory would exceed the configured count; {@code max-bytes} truncates
 * a single tape once its event bytes reach the ceiling. A value of {@code 0} disables either cap.
 * All cap and prune failures are logged and swallowed so recording never aborts a run.
 *
 * <p>
 * Note on semantics: before T831 {@code max-tapes} counted concurrently open tapes. It now counts
 * finished files on disk, matching operator expectations.
 */
@Slf4j
@RequiredArgsConstructor
public final class LocalFileTapeSink implements VhsTapeSink {

  private static final String TAPE_SUFFIX = ".vhs.jsonl";
  private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter
          .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
  private static final String FILENAME_ALLOWLIST = "[^A-Za-z0-9._-]";

  private final @NonNull CbsVhsProperties properties;
  private final @NonNull ObjectMapper objectMapper;
  private final VhsTapeWriter.@NonNull InstantSource instantSource;
  private final ConcurrentHashMap<String, VhsTapeWriter> writers = new ConcurrentHashMap<>();
  private final Object pruneLock = new Object();

  @Deprecated(forRemoval = true)
  public LocalFileTapeSink(@NonNull CbsVhsProperties properties) {
    this(properties, JsonMapper.builder().build(), Instant::now);
  }

  @Override
  public void start(
          @NonNull String runId,
          @NonNull String route,
          @Nullable String correlationId) {
    VhsTapeWriter writer = writers.computeIfAbsent(runId,
            id -> openWriter(id, route, correlationId));
    if (writer != null) {
      pruneIfNeeded();
    }
  }

  @Override
  public void append(@NonNull String runId, @NonNull TapeEvent event) {
    VhsTapeWriter writer = writers.get(runId);
    if (writer == null) {
      log.debug("Dropping VHS event for runId={}: no started tape", runId);
      return;
    }
    writer.append(event);
  }

  @Override
  public long close(@NonNull String runId) {
    VhsTapeWriter writer = writers.remove(runId);
    if (writer == null) {
      return 0L;
    }
    return writer.close();
  }

  private VhsTapeWriter openWriter(String runId, String route, String correlationId) {
    CbsVhsProperties.LocalSink local = properties.sink().local();
    if (properties.sink().type() != CbsVhsProperties.SinkType.local) {
      throw new IllegalStateException(
              "LocalFileTapeSink only supports sink.type=local, was " + properties.sink().type());
    }

    Instant now = instantSource.now();
    Path file = local.path().resolve(fileName(now, runId, correlationId));
    return new VhsTapeWriter(
            file, runId, correlationId, route, now, objectMapper, instantSource, local.maxBytes());
  }

  private void pruneIfNeeded() {
    int maxTapes = properties.sink().local().maxTapes();
    if (maxTapes <= 0) {
      return;
    }
    Path directory = properties.sink().local().path();
    synchronized (pruneLock) {
      List<Path> finished;
      try (Stream<Path> files = Files.list(directory)) {
        Set<Path> openFiles = writers.values().stream()
                .map(VhsTapeWriter::file)
                .collect(Collectors.toSet());
        finished = files
                .filter(this::isTapeFile)
                .filter(path -> !openFiles.contains(path))
                .sorted(Comparator.comparing(this::timestampPrefix))
                .toList();
      } catch (IOException ex) {
        log.warn("Failed to list VHS tapes for pruning in {}: {}", directory, ex.getMessage());
        return;
      }
      int overflow = finished.size() - maxTapes;
      if (overflow <= 0) {
        return;
      }
      for (Path path : finished.subList(0, overflow)) {
        try {
          Files.deleteIfExists(path);
          log.debug("Pruned old VHS tape {} to keep max-tapes={}", path.getFileName(), maxTapes);
        } catch (IOException ex) {
          log.warn("Failed to prune VHS tape {}: {}", path, ex.getMessage());
        }
      }
    }
  }

  private boolean isTapeFile(@NonNull Path path) {
    return Files.isRegularFile(path) && path.getFileName().toString().endsWith(TAPE_SUFFIX);
  }

  @NonNull
  private String timestampPrefix(@NonNull Path path) {
    String name = path.getFileName().toString();
    return name.length() >= 15 ? name.substring(0, 15) : name;
  }

  private static String fileName(Instant recordedAt, String runId, String correlationId) {
    String timestamp = FILE_NAME_FORMATTER.format(recordedAt);
    String safeCorrelation = correlationId == null
            ? "none"
            : correlationId.replaceAll(FILENAME_ALLOWLIST, "_");
    String safeRunId = runId.replaceAll(FILENAME_ALLOWLIST, "_");
    return timestamp + "_" + safeRunId + "_" + safeCorrelation + TAPE_SUFFIX;
  }
}
