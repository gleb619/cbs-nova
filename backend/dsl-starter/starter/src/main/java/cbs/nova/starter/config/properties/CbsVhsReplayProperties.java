package cbs.nova.starter.config.properties;

import cbs.nova.starter.vhs.replay.ReplayMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the VHS replay engine ({@code cbs.vhs.replay.*}).
 *
 * <p>
 * Replay is opt-in and off by default, and even when enabled the default target is {@code dry-run}
 * (no side effects). Production-like targets require the two-key opt-in documented on
 * {@link cbs.nova.starter.vhs.replay.VhsCallDrivers} — never weaken that guard.
 */
@Validated
@ConfigurationProperties(prefix = "cbs.vhs.replay")
public record CbsVhsReplayProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("dry-run") String target,
        @DefaultValue("false") boolean allowProduction,
        @DefaultValue("exact") ReplayMode mode,
        @DefaultValue("1") int copies,
        @DefaultValue("1.0") double speed,
        @DefaultValue("4") int concurrency,
        @DefaultValue("0") long maxDurationMs,
        @DefaultValue("false") boolean compareOutput,
        @DefaultValue("0") long minWaitMs,
        @DefaultValue("30000") long requestTimeoutMs,
        @DefaultValue("http://localhost:8080") String localBaseUrl) {

  public CbsVhsReplayProperties {
    if (copies < 1) {
      throw new IllegalArgumentException("cbs.vhs.replay.copies must be >= 1, was " + copies);
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("cbs.vhs.replay.speed must be > 0, was " + speed);
    }
    if (concurrency < 1) {
      throw new IllegalArgumentException(
              "cbs.vhs.replay.concurrency must be >= 1, was " + concurrency);
    }
    if (minWaitMs < 0) {
      throw new IllegalArgumentException(
              "cbs.vhs.replay.min-wait-ms must be >= 0, was " + minWaitMs);
    }
    if (maxDurationMs < 0) {
      throw new IllegalArgumentException(
              "cbs.vhs.replay.max-duration-ms must be >= 0 (0 = unlimited), was " + maxDurationMs);
    }
  }
}
