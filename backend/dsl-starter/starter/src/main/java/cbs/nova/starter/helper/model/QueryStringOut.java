package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code queryString} helper.
 *
 * <p>
 * {@code result} holds the operation outcome: a form-encoded {@link String} for {@code "build"} or
 * an ordered {@link java.util.Map} of decoded entries for {@code "parse"}.
 */
public record QueryStringOut(Object result) {
}
