package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code formatNumber} helper.
 *
 * <p>
 * {@code input} is either a {@link Number} ({@link java.math.BigDecimal}, {@link Double},
 * {@link Long}, ...) or a numeric {@link String} (scientific notation is accepted). {@code pattern}
 * is either a preset alias ({@code "INTEGER"}, {@code "DECIMAL"}, {@code "PERCENT"},
 * {@code "CURRENCY"}) or a raw {@link java.text.DecimalFormat} pattern. {@code locale} is optional
 * and defaults to {@link java.util.Locale#ROOT}.
 */
public record FormatNumberIn(Object input, String pattern, String locale) {
}
