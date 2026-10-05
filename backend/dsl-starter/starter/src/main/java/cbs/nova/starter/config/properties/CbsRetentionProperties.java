package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Opt-in age-based retention purges for the append-only {@code dsl_audit}, {@code dsl_events} and
 * {@code dsl_compile_diagnostics} tables (T836).
 *
 * <p>
 * Each table is controlled independently. A retention of {@code 0} or negative keeps that table
 * strictly append-only and no scheduled purge job is registered. When at least one table has a
 * positive retention, a single shared purger self-schedules and issues batched
 * {@code DELETE ... WHERE <timestamp> < cutoff LIMIT batch} passes per table.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cbs")
public class CbsRetentionProperties {

  private Audit audit = new Audit();
  private Events events = new Events();
  private Diagnostics diagnostics = new Diagnostics();

  @Data
  public static class Audit {

    /**
     * How long audit rows are kept before being purged. {@code 0} (the default) or a negative value
     * disables purging for {@code dsl_audit}.
     */
    private Duration retention = Duration.ZERO;

    private Duration purgeInterval = Duration.ofHours(1);

    @Min(1)
    private int purgeBatchSize = 500;
  }

  @Data
  public static class Events {

    /**
     * How long event rows are kept before being purged. {@code 0} (the default) or a negative value
     * disables purging for {@code dsl_events}.
     */
    private Duration retention = Duration.ZERO;

    private Duration purgeInterval = Duration.ofHours(1);

    @Min(1)
    private int purgeBatchSize = 500;
  }

  @Data
  public static class Diagnostics {

    /**
     * How long compile diagnostic rows are kept before being purged. {@code 0} (the default) or a
     * negative value disables purging for {@code dsl_compile_diagnostics}.
     */
    private Duration retention = Duration.ZERO;

    private Duration purgeInterval = Duration.ofHours(1);

    @Min(1)
    private int purgeBatchSize = 500;
  }
}
