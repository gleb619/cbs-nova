package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration knobs for the starter's actuator health indicator.
 *
 * <p>
 * Example YAML:
 *
 * <pre>
 * cbs.health:
 *   temporal:
 *     fail-status: down
 *     timeout: PT2S
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "cbs.health")
public record CbsHealthProperties(@DefaultValue Temporal temporal) {

  public CbsHealthProperties {
    temporal = temporal == null ? new Temporal(FailStatus.NONE, Duration.ofSeconds(2)) : temporal;
  }

  public enum FailStatus {
    NONE, DOWN
  }

  public record Temporal(@DefaultValue("NONE") FailStatus failStatus,
          @DefaultValue("PT2S") @NotNull Duration timeout) {
  }
}
