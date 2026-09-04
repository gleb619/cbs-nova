package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code formatYaml} helper.
 *
 * <p>
 * {@code data} may be any nested combination of {@code Map}, {@code List}, and scalars. Empty or
 * {@code null} input is rejected as a typed helper error.
 */
public record FormatYamlIn(Object data) {
}
