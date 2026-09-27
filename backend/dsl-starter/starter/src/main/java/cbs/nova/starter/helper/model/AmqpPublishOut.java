package cbs.nova.starter.helper.model;

import java.time.Instant;

/**
 * Output for the built-in {@code amqpPublish} helper.
 *
 * <p>
 * Confirms the message was handed to the broker. {@code routingKey} echoes the routing key used for
 * the publish; {@code timestamp} records when the helper sent the message.
 */
public record AmqpPublishOut(String routingKey, Instant timestamp) {
}
