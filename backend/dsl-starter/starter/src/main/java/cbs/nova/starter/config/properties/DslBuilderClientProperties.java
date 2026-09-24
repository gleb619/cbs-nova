package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the DSL builder HTTP client and its resilience primitives.
 *
 * <p>
 * Namespace is {@code cbs.dsl.builder-client.*}.
 *
 * <p>
 * T498 migrated the hand-rolled circuit breaker, bulkhead and request queue to Resilience4j core.
 * The queue's previous {@code offerTimeoutMillis} setting is retained in this record so existing
 * configuration and direct construction keep compiling, but it is no longer honoured:
 * Resilience4j's {@code ThreadPoolBulkhead} rejects immediately when its bounded queue is full.
 */
@Builder
@ConfigurationProperties(prefix = "cbs.dsl.builder-client")
@Validated
public record DslBuilderClientProperties(
        @DefaultValue("true") Boolean enabled,
        @DefaultValue("http://localhost:8091") String baseUrl,
        @DefaultValue("true") Boolean http2,
        @Valid @DefaultValue Queue queue,
        @Valid @DefaultValue Bulkhead bulkhead,
        @Valid @DefaultValue Breaker breaker,
        @Valid @DefaultValue Timeouts timeouts) {

  public DslBuilderClientProperties {
    enabled = enabled == null ? true : enabled;
    baseUrl = baseUrl == null ? "http://localhost:8091" : baseUrl;
    http2 = http2 == null ? true : http2;
    queue = queue == null ? new Queue(100, 5000L, 4) : queue;
    bulkhead = bulkhead == null ? new Bulkhead(8, 5L) : bulkhead;
    breaker = breaker == null ? new Breaker(5, 30L, 3) : breaker;
    timeouts = timeouts == null ? new Timeouts(5000L, 60000L) : timeouts;
  }

  @Builder
  public record Queue(
          @DefaultValue("100") Integer capacity,
          /**
           * Kept for backwards compatibility but ignored by the Resilience4j ThreadPoolBulkhead,
           * which rejects immediately when the bounded queue is full.
           */
          @DefaultValue("5000") Long offerTimeoutMillis,
          @DefaultValue("4") Integer workers) {

    public Queue {
      capacity = capacity == null ? 100 : capacity;
      offerTimeoutMillis = offerTimeoutMillis == null ? 5000L : offerTimeoutMillis;
      workers = workers == null ? 4 : workers;
    }
  }

  @Builder
  public record Bulkhead(
          @DefaultValue("8") Integer permits,
          @DefaultValue("5") Long acquireTimeoutSeconds) {

    public Bulkhead {
      permits = permits == null ? 8 : permits;
      acquireTimeoutSeconds = acquireTimeoutSeconds == null ? 5L : acquireTimeoutSeconds;
    }
  }

  @Builder
  public record Breaker(
          @DefaultValue("5") Integer failureThreshold,
          @DefaultValue("30") Long openDurationSeconds,
          @DefaultValue("3") Integer halfOpenProbes) {

    public Breaker {
      failureThreshold = failureThreshold == null ? 5 : failureThreshold;
      openDurationSeconds = openDurationSeconds == null ? 30L : openDurationSeconds;
      halfOpenProbes = halfOpenProbes == null ? 3 : halfOpenProbes;
    }
  }

  @Builder
  public record Timeouts(
          @DefaultValue("5000") Long connectMillis,
          @DefaultValue("60000") Long readMillis) {

    public Timeouts {
      connectMillis = connectMillis == null ? 5000L : connectMillis;
      readMillis = readMillis == null ? 60000L : readMillis;
    }
  }
}
