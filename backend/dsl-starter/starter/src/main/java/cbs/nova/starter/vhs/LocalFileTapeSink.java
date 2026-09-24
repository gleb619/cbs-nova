package cbs.nova.starter.vhs;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * {@code <recorded_at>_<runId>_<correlationId>.vhs.jsonl}. When the configured {@code maxTapes} or
 * {@code maxBytes} caps are reached, new writes are dropped and a warning is logged instead of
 * failing the execution.
 */
@Slf4j
@RequiredArgsConstructor
public final class LocalFileTapeSink implements VhsTapeSink {

  private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter
          .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final @NonNull CbsVhsProperties properties;
  private final @NonNull ObjectMapper objectMapper;
  private final VhsTapeWriter.@NonNull InstantSource instantSource;
  private final ConcurrentHashMap<String, VhsTapeWriter> writers = new ConcurrentHashMap<>();
  private final AtomicInteger openTapeCount = new AtomicInteger();

  //TODO: replace ctor with lomboks one
  @Deprecated(forRemoval = true)
  public LocalFileTapeSink(@NonNull CbsVhsProperties properties) {
    this(properties, JsonMapper.builder().build(), Instant::now);
  }

  @Override
  public void start(
          @NonNull String runId,
          @NonNull String route,
          @Nullable String correlationId) {
    writers.computeIfAbsent(runId, id -> openWriter(id, route, correlationId));
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
    openTapeCount.decrementAndGet();
    return writer.close();
  }

  private VhsTapeWriter openWriter(String runId, String route, String correlationId) {
    CbsVhsProperties.LocalSink local = properties.local();
    if (properties.sinkType() != CbsVhsProperties.SinkType.local) {
      throw new IllegalStateException(
              "LocalFileTapeSink only supports sink.type=local, was " + properties.sinkType());
    }

    int maxTapes = local.maxTapes();
    long maxBytes = local.maxBytes();
    if (maxTapes > 0 && openTapeCount.get() >= maxTapes) {
      log.warn(
              "VHS tape cap reached: maxTapes={}. Dropping tape for runId={}.",
              maxTapes, runId);
      return null;
    }
    if (maxBytes > 0) {
      log.warn(
              "VHS byte cap not yet implemented: maxBytes={}. Continuing for runId={}.",
              maxBytes, runId);
    }

    Instant now = instantSource.now();
    Path file = local.path().resolve(fileName(now, runId, correlationId));
    openTapeCount.incrementAndGet();
    return new VhsTapeWriter(file, runId, correlationId, route, now, objectMapper, instantSource);
  }

  private static String fileName(Instant recordedAt, String runId, String correlationId) {
    String timestamp = FILE_NAME_FORMATTER.format(recordedAt);
    String safeCorrelation = correlationId == null ? "none" : correlationId.replace('/', '_');
    String safeRunId = runId.replace('/', '_');
    return timestamp + "_" + safeRunId + "_" + safeCorrelation + ".vhs.jsonl";
  }
}
