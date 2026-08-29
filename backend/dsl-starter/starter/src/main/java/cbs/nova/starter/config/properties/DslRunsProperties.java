package cbs.nova.starter.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for DSL run payload size caps.
 *
 * <p>
 * Both caps are applied defensively: oversized inputs are rejected with HTTP 413 before any
 * persistence or workflow submission, and oversized worker-side outputs are truncated on persist
 * while the run still completes normally.
 *
 * <p>
 * A value of {@code 0} or a very large value effectively disables the corresponding cap.
 */
@Data
@ConfigurationProperties(prefix = "cbs.runs")
public class DslRunsProperties {

  public static final long DEFAULT_MAX_BYTES = 1024L * 1024L;

  /** Maximum allowed size of an incoming run/preview request body in bytes. */
  private long maxInputBytes = DEFAULT_MAX_BYTES;

  /** Maximum allowed size of a persisted run output JSON payload in bytes. */
  private long maxOutputBytes = DEFAULT_MAX_BYTES;
}
