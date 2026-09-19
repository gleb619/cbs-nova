package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single entry point for creating {@link VhsCallDriver} instances.
 *
 * <p>
 * <b>Operational safety — do not weaken.</b> The replay engine re-executes real, previously
 * recorded actions. Pointing a tape at a production-like target could therefore mutate real data a
 * second time. To make that impossible via a single configuration slip, any target other than
 * {@code dry-run} (no side effects) or {@code local} (assumed dev/staging) requires <b>both</b> of:
 *
 * <ol>
 * <li>{@code cbs.vhs.replay.allow-production=true} (application configuration), and</li>
 * <li>{@code CBS_VHS_REPLAY_ALLOW_PRODUCTION=1} (operator-set environment variable).</li>
 * </ol>
 *
 * The default target is {@code dry-run}.
 */
public final class VhsCallDrivers {

  /** Environment variable that acts as the second, independent production opt-in key. */
  public static final String PRODUCTION_ENV_KEY = "CBS_VHS_REPLAY_ALLOW_PRODUCTION";

  private VhsCallDrivers() {
  }

  /**
   * Resolve the configured target to a driver, enforcing the two-key production opt-in guard.
   *
   * @throws VhsReplayException
   *           when the target is production-like and both opt-in keys are not present
   */
  public static VhsCallDriver resolve(@NonNull CbsVhsReplayProperties properties) {
    return resolve(properties, JsonMapper.builder().build(),
            System.getenv(PRODUCTION_ENV_KEY));
  }

  /**
   * Test-friendly variant with explicit mapper and env-flag value.
   */
  static VhsCallDriver resolve(
          @NonNull CbsVhsReplayProperties properties,
          @NonNull ObjectMapper objectMapper,
          @Nullable String productionEnvFlag) {
    String target = properties.target() == null ? "" : properties.target().trim().toLowerCase();
    if (target.isEmpty() || "dry-run".equals(target) || "dryrun".equals(target)) {
      return new DryRunCallDriver();
    }
    if ("local".equals(target)) {
      return new LocalBackendCallDriver(
              properties.localBaseUrl(), properties.requestTimeoutMs(), objectMapper);
    }
    requireProductionOptIn(properties, target, productionEnvFlag);
    return new LocalBackendCallDriver(target, properties.requestTimeoutMs(), objectMapper);
  }

  static void requireProductionOptIn(
          CbsVhsReplayProperties properties, String target, @Nullable String productionEnvFlag) {
    boolean configKey = properties.allowProduction();
    boolean envKey = "1".equals(productionEnvFlag == null ? null : productionEnvFlag.trim());
    if (!configKey || !envKey) {
      throw new VhsReplayException(
              "Refusing to replay against production-like target '" + target + "'. This requires"
                      + " BOTH opt-in keys: cbs.vhs.replay.allow-production=true AND environment"
                      + " variable " + PRODUCTION_ENV_KEY + "=1 (config key present: "
                      + configKey + ", env key present: " + envKey + ").");
    }
  }
}
