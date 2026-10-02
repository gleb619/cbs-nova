package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web-layer global caps applied to incoming HTTP requests before they reach a controller.
 *
 * <p>
 * Today the only payload-size cap is the per-run {@code cbs.runs.max-input-bytes} enforced by
 * {@code DslPayloadSizeValidator} — and that validator only runs for the run/preview/hierarchy/
 * explain handlers. ~20 other write handlers (drafts, files, docs, signals, schedules, promote,
 * change-request, drafts vcs, api-key admin, notification rules, vhs management) accept an
 * unbounded body. {@code cbs.nova.web.max-request-bytes} is the cheap, app-wide ceiling that
 * rejects oversized requests before they are buffered or deserialised.
 *
 * <p>
 * A value of {@code 0} (or negative) disables the global cap entirely — useful for tests and
 * trusted-loopback deployments.
 */
@ConfigurationProperties(prefix = "cbs.nova.web")
public record CbsNovaWebProperties(
        @DefaultValue("10485760") long maxRequestBytes) {

  public CbsNovaWebProperties {
    if (maxRequestBytes < 0) {
      maxRequestBytes = 0;
    }
  }
}
