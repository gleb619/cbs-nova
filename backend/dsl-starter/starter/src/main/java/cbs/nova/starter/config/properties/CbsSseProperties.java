package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Caps shared by both SSE services in the starter
 * ({@code cbs.nova.starter.sse.ExecutionSseService},
 * {@code cbs.nova.starter.sse.DryRunLogSseService}). Each service holds one
 * {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter} per run id inside a
 * {@link java.util.concurrent.ConcurrentHashMap}; without a cap a single client can open an
 * unbounded number of distinct long-lived streams (each holding a servlet async context + a map
 * entry) — only the heartbeat noticing a dead socket frees them.
 *
 * <p>
 * Example YAML:
 *
 * <pre>
 * cbs.sse:
 *   max-subscribers: 500
 * </pre>
 *
 * <p>
 * The cap is applied independently to each service (one counter per service, default 500 each). A
 * subscriber that re-subscribes with the same id <em>replaces</em> the previous emitter and is not
 * counted twice. A subscriber whose id differs from every existing entry is rejected with
 * {@code 429 Too Many Requests} (and the emitter is never created) when the cap is already reached.
 */
@Validated
@ConfigurationProperties(prefix = "cbs.sse")
public record CbsSseProperties(
        @DefaultValue("500") @Min(1) int maxSubscribers) {

  public CbsSseProperties {
    if (maxSubscribers < 1) {
      maxSubscribers = 500;
    }
  }
}
