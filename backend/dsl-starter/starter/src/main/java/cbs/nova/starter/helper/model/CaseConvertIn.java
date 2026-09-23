package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code caseConvert} helper.
 *
 * <p>
 * {@code input} is required and must be non-empty. {@code mode} must be one of {@code "camel"},
 * {@code "kebab"}, {@code "snake"}, or {@code "title"} (case-insensitive).
 */
public record CaseConvertIn(String input, String mode) {
}
