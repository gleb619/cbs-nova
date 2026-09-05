package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code random} helper.
 *
 * <p>
 * The runtime type of {@code result} depends on the selected {@code mode}: {@link Integer} for
 * {@code "int"}, {@link Long} for {@code "long"}, {@link Double} for {@code "double"},
 * {@link String} for {@code "string"}, and an element of the input list for {@code "choice"}.
 */
public record RandomOut(Object result) {
}
