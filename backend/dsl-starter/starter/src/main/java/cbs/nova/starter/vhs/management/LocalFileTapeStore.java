package cbs.nova.starter.vhs.management;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.vhs.replay.VhsReplayException;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

/**
 * Local file-system implementation of {@link VhsTapeStore}.
 *
 * <p>
 * Scans {@code cbs.vhs.sink.local.path} for files ending in {@code .vhs.jsonl}. Tape metadata is
 * read from the header line; event counts are produced by a full read/validate pass through
 * {@link VhsTapeReader} so the listing always reflects actual, closed tapes.
 */
@Slf4j
public final class LocalFileTapeStore implements VhsTapeStore {

  private static final String TAPE_SUFFIX = ".vhs.jsonl";

  private final Path directory;
  private final ObjectMapper objectMapper;

  public LocalFileTapeStore(@NonNull CbsVhsProperties properties,
          @NonNull ObjectMapper objectMapper) {
    this.directory = properties.local().path();
    this.objectMapper = objectMapper;
  }

  @Override
  @NonNull
  public List<TapeSummary> list() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
              .filter(this::isTapeFile)
              .map(this::toSummary)
              .flatMap(Optional::stream)
              .sorted(Comparator.comparing(TapeSummary::recordedAt).reversed())
              .toList();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to list VHS tapes in " + directory, ex);
    }
  }

  @Override
  @NonNull
  public Optional<TapeSummary> findByRunId(@NonNull String runId) {
    return list().stream()
            .filter(t -> runId.equals(t.runId()))
            .findFirst();
  }

  @Override
  @NonNull
  public Optional<Path> findPath(@NonNull String runId) {
    if (!Files.isDirectory(directory)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
              .filter(this::isTapeFile)
              .filter(path -> runId
                      .equals(readTape(path).map(t -> t.header().sourceRunId()).orElse(null)))
              .findFirst();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to locate VHS tape for runId=" + runId, ex);
    }
  }

  @Override
  @NonNull
  public Optional<InputStream> openStream(@NonNull String runId) {
    return findPath(runId).map(path -> {
      try {
        return Files.newInputStream(path);
      } catch (IOException ex) {
        throw new UncheckedIOException("Failed to open VHS tape: " + path, ex);
      }
    });
  }

  @Override
  public boolean delete(@NonNull String runId) {
    return findPath(runId).map(path -> {
      try {
        return Files.deleteIfExists(path);
      } catch (IOException ex) {
        throw new UncheckedIOException("Failed to delete VHS tape: " + path, ex);
      }
    }).orElse(false);
  }

  private boolean isTapeFile(@NonNull Path path) {
    return Files.isRegularFile(path) && path.getFileName().toString().endsWith(TAPE_SUFFIX);
  }

  @NonNull
  private Optional<TapeSummary> toSummary(@NonNull Path path) {
    return readTape(path).map(t -> {
      long size;
      try {
        size = Files.size(path);
      } catch (IOException ex) {
        log.warn("Could not determine size of VHS tape {}: {}", path, ex.getMessage());
        size = 0L;
      }
      return new TapeSummary(
              path.getFileName().toString(),
              Instant.parse(t.header().recordedAt()),
              t.header().sourceRunId(),
              t.header().correlationId(),
              t.header().route(),
              size,
              t.events().size());
    });
  }

  @NonNull
  private Optional<VhsTapeReader.VhsTape> readTape(@NonNull Path path) {
    try {
      return Optional.of(new VhsTapeReader(objectMapper).read(path));
    } catch (VhsReplayException ex) {
      log.warn("Skipping malformed VHS tape {}: {}", path, ex.getMessage());
      return Optional.empty();
    }
  }
}
