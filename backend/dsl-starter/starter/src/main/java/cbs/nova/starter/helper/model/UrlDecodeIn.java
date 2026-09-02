package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code urlDecode} helper.
 *
 * <p>
 * {@code input} is required and must be non-empty. {@code charset} defaults to {@code "UTF-8"} when
 * null or blank. {@code form} defaults to {@code false}; when {@code true} {@code +} is treated as
 * a space, otherwise a literal {@code +} is preserved.
 */
public record UrlDecodeIn(String input, String charset, Boolean form) {
}
