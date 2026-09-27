package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code amqpPublish} helper.
 *
 * <p>
 * {@code exchange} and {@code routingKey} target the RabbitMQ exchange. {@code payload} must be a
 * valid JSON string; it is sent as the message body with content-type {@code application/json}.
 */
public record AmqpPublishIn(String exchange, String routingKey, String payload) {
}
