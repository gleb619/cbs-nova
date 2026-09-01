package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code formatDate} helper.
 *
 * <p>
 * {@code input} is either an ISO-8601 date/time string or a numeric epoch-millis value.
 * {@code pattern} is either a preset alias such as {@code "ISO_INSTANT"} or a raw
 * {@link java.time.format.DateTimeFormatter} pattern. {@code zone} is optional and defaults to
 * {@code "UTC"}.
 */
public record FormatDateIn(String input, String pattern, String zone) {
}
