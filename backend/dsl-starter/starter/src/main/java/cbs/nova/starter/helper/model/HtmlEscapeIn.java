package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code htmlEscape} helper.
 *
 * <p>
 * {@code input} is required and must be non-empty.
 */
public record HtmlEscapeIn(String input) {
}
