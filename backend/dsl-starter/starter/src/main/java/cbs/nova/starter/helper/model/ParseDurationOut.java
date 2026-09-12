package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code parseDuration} helper.
 *
 * <p>
 * {@code millis} is the total duration in milliseconds, {@code seconds} is the floored seconds
 * ({@link java.time.Duration#toSeconds()}), and {@code iso} is the normalized ISO-8601 duration
 * string ({@link java.time.Duration#toString()}).
 */
public record ParseDurationOut(long millis, long seconds, String iso) {
}
