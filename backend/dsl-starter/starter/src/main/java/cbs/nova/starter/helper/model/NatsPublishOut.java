package cbs.nova.starter.helper.model;

import java.time.Instant;

/**
 * Output for the built-in {@code natsPublish} helper.
 *
 * <p>
 * Confirms the message was published. {@code subject} echoes the destination subject and
 * {@code timestamp} records when the helper invoked the NATS {@code Connection}.
 */
public record NatsPublishOut(String subject, Instant timestamp) {
}
