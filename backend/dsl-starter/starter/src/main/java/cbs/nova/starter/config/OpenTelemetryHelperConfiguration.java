package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.OpenTelemetryHelperProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the {@link cbs.nova.starter.helper.OpenTelemetryHelper} singleton's bounded-state
 * dependencies:
 *
 * <ul>
 * <li>{@link OpenTelemetryHelperProperties} — the {@code cbs.otel.helper.*} namespace defaults (max
 * entries, TTLs, max distinct run-ids).</li>
 * <li>{@link Clock} — injectable so TTL-driven cleanup can be driven by a {@code Clock.fixed(...)}
 * in tests without sleeping.</li>
 * </ul>
 *
 * <p>
 * Host applications may override the {@link Clock} bean (e.g. {@code @Primary Clock}) to install a
 * deterministic clock for tests.
 */
@Configuration
@EnableConfigurationProperties(OpenTelemetryHelperProperties.class)
public class OpenTelemetryHelperConfiguration {

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
  Clock otelHelperClock() {
    return Clock.systemUTC();
  }
}
