package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the built-in {@code otel} helper's process-wide state.
 *
 * <p>
 * The helper is a Spring singleton and tracks open spans + per-run baggage in JVM memory. Without
 * bounds, a single leaked {@code span()} (or a process that runs forever) can grow the maps without
 * limit and a single shared process-wide baggage store lets run A read run B's keys. These knobs
 * bound the helper's state and let it lazy-clean stale entries on the next helper call (no
 * background scheduler thread).
 *
 * <p>
 * All sizes are upper bounds. Entries older than the TTL are ended with status
 * {@code ERROR}/"abandoned" (spans) or silently dropped (baggage) by the next
 * {@code span()}/{@code endSpan()} call. When inserting a span would exceed {@link #spanMaxEntries}
 * the oldest open span is ended with {@code ERROR}/"evicted" rather than silently dropped.
 */
@ConfigurationProperties(prefix = "cbs.otel.helper")
@Validated
public record OpenTelemetryHelperProperties(
        /**
         * Maximum number of concurrently open spans tracked by the helper. When exceeded, the
         * oldest entry is ended with status {@code ERROR}/"evicted" before the new one is added.
         */
        @Min(1) @DefaultValue("1000") int spanMaxEntries,
        /**
         * Maximum age of an entry before it is considered abandoned and reaped by lazy cleanup.
         * Triggers a span-end with {@code ERROR}/"abandoned" (spans) or silent drop (baggage).
         */
        @DefaultValue("PT10M") Duration spanTtl,
        /**
         * Maximum age of a baggage value before it is silently dropped by lazy cleanup.
         */
        @DefaultValue("PT10M") Duration baggageTtl,
        /**
         * Maximum number of distinct run-ids whose baggage the helper tracks concurrently. When
         * exceeded, the oldest run's entries are dropped silently before the new one is added.
         */
        @Min(1) @DefaultValue("1000") int baggageMaxRuns) {
}
