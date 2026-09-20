package cbs.nova.starter.config.properties;

import cbs.nova.starter.vhs.replay.ReplayMode;
import cbs.nova.starter.vhs.replay.fake.FakeRule;
import java.util.List;
import java.util.Map;
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
 *
 * <p>
 * Replay-time faking ({@code cbs.vhs.replay.faking.*}) replaces recorded real identifiers, secrets,
 * and references with synthetic equivalents between the tape reader and the call driver so that
 * lower environments can reproduce a bug without the original production data existing there.
 * Distinct from T556's record-time scrubbing.
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
        @DefaultValue("http://localhost:8080") String localBaseUrl,
        @DefaultValue Faking faking) {

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
    faking = faking == null ? Faking.disabled() : faking;
  }

  /**
   * Replay-time faking configuration ({@code cbs.vhs.replay.faking.*}).
   *
   * <p>
   * The {@link #enabled()} flag defaults to {@code false} and is honoured literally by the replay
   * engine — when the operator wants faking on by default for non-dry-run targets but off for
   * dry-run, they should configure it explicitly. {@code cbs.vhs.replay.faking.enabled} is the
   * single source of truth; the target-aware default that the plan describes ("on for non-dry-run,
   * off for dry-run") is layered on top in {@code VhsReplayEngine} so the YAML key stays a plain
   * boolean.
   *
   * @param enabled
   *          master switch; when {@code false} the faker is a no-op pass-through
   * @param rules
   *          ordered fake rules; empty list is a no-op
   * @param lookupTable
   *          pre-seeded real→synthetic mappings for identifiers that must be stable across runs
   * @param seed
   *          seed for the {@code synthetic_hash} generator; {@code null} / blank falls back to a
   *          deterministic built-in seed
   */
  public record Faking(
          @DefaultValue("false") boolean enabled,
          @DefaultValue List<FakeRule> rules,
          @DefaultValue Map<String, String> lookupTable,
          @DefaultValue("vhs-replay") String seed) {

    public Faking {
      rules = rules == null ? List.of() : List.copyOf(rules);
      lookupTable = lookupTable == null ? Map.of() : Map.copyOf(lookupTable);
      seed = seed == null || seed.isBlank() ? "vhs-replay" : seed;
    }

    public static Faking disabled() {
      return new Faking(false, List.of(), Map.of(), "vhs-replay");
    }
  }
}
