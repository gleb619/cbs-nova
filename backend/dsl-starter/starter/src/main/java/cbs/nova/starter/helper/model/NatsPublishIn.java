package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code natsPublish} helper.
 *
 * <p>
 * {@code subject} is the NATS Core subject to publish to. {@code payload} must be a valid JSON
 * string; it is sent as UTF-8 bytes.
 */
public record NatsPublishIn(String subject, String payload) {
}
