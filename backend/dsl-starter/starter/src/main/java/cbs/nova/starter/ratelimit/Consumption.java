package cbs.nova.starter.ratelimit;

/**
 * Result of a single rate-limit consumption attempt.
 */
public record Consumption(boolean allowed, long retryAfterSeconds) {
}
