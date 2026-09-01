package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code uuidV7} helper.
 *
 * <p>
 * The optional {@code namespace} is mixed into the random tail of the UUID. When supplied, the 62
 * random trailing bits are derived deterministically from {@code SHA-256(namespace)}, making the
 * final 12-character (node) group repeatable for that namespace while the timestamp and monotonic
 * counter keep the overall value ordered and unique.
 */
public record UuidV7In(String namespace) {
}
