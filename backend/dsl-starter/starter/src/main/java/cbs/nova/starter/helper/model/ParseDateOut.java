package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code parseDate} helper.
 *
 * <p>
 * {@code iso} is the parsed instant formatted as an ISO-8601 string ending in {@code Z}.
 */
public record ParseDateOut(String iso) {
}
