package cbs.nova.starter.config;

import java.time.Duration;
import org.jspecify.annotations.NonNull;

/**
 * Holds the Temporal workflow execution and task timeouts used by both API-launched runs and
 * schedule-fired runs. Kept in one bean so the defaults are read from the environment once.
 */
public record TemporalProcessTimeouts(
        @NonNull Duration executionTimeout,
        @NonNull Duration taskTimeout) {
}
