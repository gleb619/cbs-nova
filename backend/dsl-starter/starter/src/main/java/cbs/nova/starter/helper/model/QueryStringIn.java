package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Input for the built-in {@code queryString} helper.
 *
 * <p>
 * {@code mode} is required and selects the operation (case-insensitive):
 * <ul>
 * <li>{@code "build"} — joins {@code params} into a form-encoded query string.</li>
 * <li>{@code "parse"} — splits {@code queryString} into an ordered key/value map.</li>
 * </ul>
 *
 * <p>
 * For {@code "build"}, {@code params} must be non-null. For {@code "parse"}, {@code queryString}
 * must be non-null.
 */
public record QueryStringIn(String mode, Map<String, String> params, String queryString) {
}
