package cbs.nova.starter.vhs;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.vhs.VhsTapeWriter.InstantSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * S3-compatible object-storage implementation of {@link VhsTapeSink}.
 *
 * <p>
 * Writes one tape object per run to {@code s3://<bucket>/<prefix>/<runId>.vhs.jsonl}. Events are
 * serialized into a bounded local staging file and the completed object is uploaded on
 * {@link #close}; until {@code close} succeeds no object is visible in S3, so an aborted run leaves
 * no half-written tape. The staging file is deleted after upload. {@code max-bytes} caps the
 * staging file and triggers a {@code tape_truncated} marker when exceeded, matching
 * {@link LocalFileTapeSink} semantics.
 *
 * <p>
 * The actual {@link S3Client} bean is supplied by the host application (opt-in, compileOnly AWS SDK
 * v2), following the same pattern as {@link cbs.nova.starter.helper.S3StorageHelper}. When
 * {@code sink.type=s3} is selected without an {@code S3Client} bean the context fails fast.
 */
@Slf4j
public final class S3TapeSink implements VhsTapeSink {

  private static final String TAPE_SUFFIX = ".vhs.jsonl";
  private static final String KEY_ALLOWLIST = "[^A-Za-z0-9._-]";
  private static final DateTimeFormatter STAMP_FORMATTER = DateTimeFormatter
          .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final @NonNull CbsVhsProperties properties;
  private final @NonNull ObjectMapper objectMapper;
  private final @NonNull S3Client s3Client;
  private final InstantSource instantSource;
  private final @NonNull Path stagingDir;
  private final ConcurrentHashMap<String, VhsTapeWriter> writers = new ConcurrentHashMap<>();

  public S3TapeSink(
          @NonNull CbsVhsProperties properties,
          @NonNull ObjectProvider<S3Client> s3ClientProvider,
          @NonNull ObjectMapper objectMapper) {
    this(properties, s3ClientProvider.getIfAvailable(), objectMapper, Instant::now,
            Path.of(System.getProperty("java.io.tmpdir"), "cbs-nova", "vhs-s3-staging"));
  }

  S3TapeSink(
          @NonNull CbsVhsProperties properties,
          @Nullable S3Client s3Client,
          @NonNull ObjectMapper objectMapper,
          InstantSource instantSource,
          @NonNull Path stagingDir) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.s3Client = s3Client;
    this.instantSource = instantSource;
    this.stagingDir = stagingDir;

    if (properties.sink().type() != CbsVhsProperties.SinkType.s3) {
      throw new IllegalStateException(
              "S3TapeSink only supports sink.type=s3, was " + properties.sink().type());
    }
    if (s3Client == null) {
      throw new IllegalStateException(
              "S3TapeSink requires an S3Client bean; ensure software.amazon.awssdk:s3 is on the"
                      + " classpath and an S3Client bean is configured");
    }
    String bucket = properties.sink().s3().bucket();
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("S3TapeSink requires cbs.vhs.sink.s3.bucket");
    }
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
    Path file = writer.file();
    long count;
    try {
      count = writer.close();
      upload(file, objectKey(runId));
    } finally {
      cleanup(file);
    }
    return count;
  }

  private VhsTapeWriter openWriter(String runId, String route, String correlationId) {
    Instant now = instantSource.now();
    Path file = stagingFile(runId, now);
    long maxBytes = properties.sink().s3().maxBytes();
    return new VhsTapeWriter(file, runId, correlationId, route, now, objectMapper, instantSource,
            maxBytes);
  }

  private Path stagingFile(String runId, Instant now) {
    String timestamp = STAMP_FORMATTER.format(now);
    String safeRunId = runId.replaceAll(KEY_ALLOWLIST, "_");
    String prefix = timestamp + "_" + safeRunId + "_" + System.nanoTime();
    try {
      Files.createDirectories(stagingDir);
      return Files.createTempFile(stagingDir, prefix + "_", TAPE_SUFFIX);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to create S3 tape staging file for runId=" + runId,
              ex);
    }
  }

  private String objectKey(String runId) {
    String prefix = properties.sink().s3().prefix();
    String safeRunId = runId.replaceAll(KEY_ALLOWLIST, "_");
    if (prefix == null || prefix.isBlank()) {
      return safeRunId + TAPE_SUFFIX;
    }
    String normalized = prefix.endsWith("/") ? prefix : prefix + "/";
    return normalized + safeRunId + TAPE_SUFFIX;
  }

  private void upload(Path file, String key) {
    String bucket = properties.sink().s3().bucket();
    long size;
    try {
      size = Files.size(file);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to size VHS tape staging file: " + file, ex);
    }
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/x-jsonlines")
            .contentLength(size)
            .build();
    try (InputStream in = Files.newInputStream(file)) {
      s3Client.putObject(request, RequestBody.fromInputStream(in, size));
      log.debug("Uploaded VHS tape to s3://{}/{}", bucket, key);
    } catch (IOException ex) {
      throw new UncheckedIOException(
              "Failed to upload VHS tape to s3://" + bucket + "/" + key, ex);
    }
  }

  private void cleanup(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ex) {
      log.warn("Failed to delete S3 tape staging file {}: {}", file, ex.getMessage());
    }
  }
}
