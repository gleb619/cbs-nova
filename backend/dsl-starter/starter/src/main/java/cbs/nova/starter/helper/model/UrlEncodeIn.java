package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code urlEncode} helper.
 *
 * <p>
 * {@code input} is required and must be non-empty. {@code charset} defaults to {@code "UTF-8"} when
 * null or blank. {@code form} defaults to {@code false}; when {@code true} spaces are encoded as
 * {@code +} for {@code application/x-www-form-urlencoded} bodies, otherwise as {@code %20}.
 */
public record UrlEncodeIn(String input, String charset, Boolean form) {
}
