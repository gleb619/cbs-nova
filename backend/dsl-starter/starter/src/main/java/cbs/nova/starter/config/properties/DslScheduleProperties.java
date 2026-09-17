package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration properties for Temporal schedules created by {@code DslScheduleService}.
 */
@ConfigurationProperties(prefix = "cbs.nova.schedule")
public record DslScheduleProperties(
        /**
         * How long after a missed fire Temporal still catches it up. Fires missed beyond this
         * window (e.g. during a Temporal outage) are silently dropped — they do not backfill.
         */
        @DefaultValue("1m") Duration catchupWindow) {
}
