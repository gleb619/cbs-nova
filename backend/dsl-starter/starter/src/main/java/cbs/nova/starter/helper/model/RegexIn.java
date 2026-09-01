package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code regex} helper.
 *
 * <p>
 * {@code op} must be one of {@code "match"}, {@code "extract"}, {@code "replace"}, or
 * {@code "split"} (case-insensitive). {@code pattern} is required; {@code input} is required but
 * may be empty. {@code replacement} defaults to {@code ""} for replace operations. {@code group}
 * defaults to {@code 0} for extract operations; {@code groupName} takes precedence over
 * {@code group} when non-blank.
 */
public record RegexIn(String op, String pattern, String input, String replacement, Integer group,
        String groupName) {
}
