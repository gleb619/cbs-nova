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
 * Recording is opt-in and off by default. When {@code enabled=false} no sink or recorder beans are
 * registered and the hot execution path is unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "cbs.vhs")
public record CbsVhsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<String> recordableRoutes,
        @DefaultValue("local") SinkType sinkType,
        @Valid @DefaultValue LocalSink local,
        @Valid @DefaultValue Scrub scrub) {

  public CbsVhsProperties {
    recordableRoutes = recordableRoutes == null ? List.of() : List.copyOf(recordableRoutes);
    sinkType = sinkType == null ? SinkType.local : sinkType;
    local = local == null ? new LocalSink(null, 0, 0L) : local;
    scrub = scrub == null ? new Scrub(false, List.of(), VhsScrubber.DEFAULT_MASK, null) : scrub;
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
   * Supported sink backends. Only {@code local} is implemented; {@code object_storage} is reserved
   * for a follow-up task.
   */
  public enum SinkType {
    local, object_storage
  }
}
