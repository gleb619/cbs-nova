package cbs.nova.starter.config.properties;

import cbs.nova.starter.vhs.scrub.ScrubRule;
import cbs.nova.starter.vhs.scrub.VhsScrubber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the VHS execution recorder ({@code cbs.vhs.*}).
 *
 * <p>
 * Recording is opt-in and off by default. When {@code enabled=false} no sink / scrub / recorder
 * beans are registered and the hot execution path is unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "cbs.vhs")
public record CbsVhsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<String> recordableRoutes,
        @Valid @DefaultValue Sink sink,
        @Valid @DefaultValue Scrub scrub) {

  public CbsVhsProperties {
    recordableRoutes = recordableRoutes == null ? List.of() : List.copyOf(recordableRoutes);
    sink = sink == null ? new Sink(SinkType.local, null, null) : sink;
    scrub = scrub == null ? new Scrub(false, List.of(), VhsScrubber.DEFAULT_MASK, null) : scrub;
  }

  /**
   * Tape sink selection and per-backend settings ({@code cbs.vhs.sink.*}).
   */
  public record Sink(
          @DefaultValue("local") SinkType type,
          @Valid @DefaultValue LocalSink local,
          @Valid @DefaultValue S3Sink s3) {

    public Sink {
      type = type == null ? SinkType.local : type;
      local = local == null ? new LocalSink(null, 0, 0L) : local;
      s3 = s3 == null ? new S3Sink(null, null, null, false, 104_857_600L) : s3;
    }
  }

  /**
   * Supported sink backends.
   */
  public enum SinkType {
    local, s3
  }

  /**
   * Local file-system tape sink settings.
   *
   * <p>
   * {@code max-tapes} caps the number of finished {@code .vhs.jsonl} files kept in the sink
   * directory; when the limit is exceeded the oldest files (by the embedded timestamp prefix in the
   * filename) are deleted. It does <strong>not</strong> limit concurrently open recordings.
   * {@code max-bytes} is a per-tape byte ceiling: once the running total of event-line bytes would
   * exceed it, the tape stops accepting events and writes a {@code tape_truncated} marker. A value
   * of {@code 0} disables the corresponding cap.
   */
  public record LocalSink(
          Path path,
          @DefaultValue("0") @Min(0) int maxTapes,
          @DefaultValue("0") @Min(0) long maxBytes) {

    public LocalSink {
      if (path == null) {
        path = Path.of(System.getProperty("java.io.tmpdir"), "cbs-nova", "vhs");
      }
      if (maxTapes < 0) {
        maxTapes = 0;
      }
      if (maxBytes < 0) {
        maxBytes = 0L;
      }
    }
  }

  /**
   * S3-compatible object-storage tape sink settings ({@code cbs.vhs.sink.s3.*}).
   *
   * <p>
   * The actual {@code S3Client} bean (including any endpoint override or path-style access for
   * MinIO) must be supplied by the host application, matching the opt-in pattern used by
   * {@code S3StorageHelper}. The sink only needs bucket and prefix; endpoint and path-style keys
   * are accepted here so operators keep all S3-related VHS knobs under one namespace.
   *
   * <p>
   * {@code max-bytes} bounds the on-disk staging file used while a tape is open; once it would be
   * exceeded the writer records a {@code tape_truncated} marker and closes the tape normally. The
   * default cap prevents an unbounded in-memory or on-disk buffer per open run.
   */
  public record S3Sink(
          String bucket,
          String prefix,
          String endpointOverride,
          @DefaultValue("false") boolean pathStyle,
          @DefaultValue("104857600") @Min(0) long maxBytes) {

    public S3Sink {
      if (prefix == null) {
        prefix = "";
      }
      if (maxBytes < 0) {
        maxBytes = 0L;
      }
    }
  }

  /**
   * Record-time scrubbing settings ({@code cbs.vhs.scrub.*}).
   */
  public record Scrub(
          @DefaultValue("false") boolean enabled,
          @DefaultValue List<ScrubRule> rules,
          @DefaultValue(VhsScrubber.DEFAULT_MASK) String maskValue,
          String seed) {

    public Scrub {
      rules = rules == null ? List.of() : List.copyOf(rules);
      maskValue = maskValue == null || maskValue.isBlank() ? VhsScrubber.DEFAULT_MASK : maskValue;
    }
  }
}
