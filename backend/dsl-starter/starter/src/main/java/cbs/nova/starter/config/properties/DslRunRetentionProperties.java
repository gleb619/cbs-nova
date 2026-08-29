package cbs.nova.starter.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the recurring {@code dsl_runs} retention purge.
 *
 * <p>
 * Follows the established opt-in pattern for behavior changes in this project (see the OIDC guard):
 * the purge is <em>disabled by default</em> and an operator explicitly opts in by setting
 * {@code cbs.runs.retention} to a positive duration. Local DX and existing deployments are
 * therefore unaffected until a retention is configured.
 */
@Data
@ConfigurationProperties(prefix = "cbs.runs")
public class DslRunRetentionProperties {

  /**
   * How long finished runs are kept before being purged. {@code 0} (the default) or a negative
   * value disables the scheduled purge entirely — no job is registered and nothing is deleted.
   */
  private Duration retention = Duration.ZERO;

  /** How often the scheduled purge runs. */
  private Duration purgeInterval = Duration.ofHours(1);

  /** Max rows removed per batched delete pass, keeping each schema/row lock small. */
  private int purgeBatchSize = 500;
}
