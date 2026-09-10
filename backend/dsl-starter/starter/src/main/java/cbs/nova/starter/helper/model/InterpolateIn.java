package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Input for the built-in {@code interpolate} helper.
 *
 * <p>
 * Performs safe literal {@code ${key}} substitution — no expression evaluation, suitable for
 * operator- or user-supplied templates where {@code formatMessage}'s full expression engine would
 * be a SpEL-injection surface.
 *
 * @param template
 *          the template string with {@code ${key}} placeholders; required.
 * @param params
 *          values for the placeholders; {@code null} is treated as an empty map.
 * @param onMissing
 *          case-insensitive policy for keys absent from {@code params}: {@code "error"} (default —
 *          unknown key fails), {@code "empty"} (unknown key renders as {@code ""}), or
 *          {@code "keep"} (unknown key is left verbatim as {@code ${key}}). Unrecognized values
 *          fail.
 */
public record InterpolateIn(
        @Nullable String template,
        @Nullable Map<String, Object> params,
        @Nullable String onMissing) {
}
