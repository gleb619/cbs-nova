package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code parseDuration} helper.
 *
 * <p>
 * {@code value} is the duration string to parse. Accepts ISO-8601 duration strings (e.g.
 * {@code "PT1H30M"}, {@code "P2DT3H"}, {@code "PT0.5S"}) and shorthand made of one or more
 * {@code <number><unit>} segments (e.g. {@code "90m"}, {@code "1h30m"}, {@code "250ms"}).
 * {@code null} and blank values are rejected.
 */
public record ParseDurationIn(String value) {
}
