package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code parseDate} helper.
 *
 * <p>
 * {@code input} is a date/time string that matches {@code pattern}. {@code pattern} is either a
 * preset alias such as {@code "ISO_INSTANT"} or a raw {@link java.time.format.DateTimeFormatter}
 * pattern. {@code zone} is optional and defaults to {@code "UTC"}.
 */
public record ParseDateIn(String input, String pattern, String zone) {
}
