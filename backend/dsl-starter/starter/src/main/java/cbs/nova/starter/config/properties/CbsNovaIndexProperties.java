package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Opt-in root index page.
 *
 * <p>
 * Set {@code cbs.nova.index-page.enabled=true} to expose {@code GET /} as a tiny HTML dashboard when the
 * request looks like it came from a browser.
 */
@ConfigurationProperties(prefix = "cbs.nova.index-page")
public record CbsNovaIndexProperties(@DefaultValue("false") boolean enabled) {
}
