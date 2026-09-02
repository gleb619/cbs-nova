package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code sha256} helper.
 *
 * <p>
 * {@code result} contains the SHA-256 digest of the input encoded with the requested format.
 */
public record Sha256Out(String result) {
}
