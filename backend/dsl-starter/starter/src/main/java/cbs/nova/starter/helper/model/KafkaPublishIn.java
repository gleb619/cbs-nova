package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code kafkaPublish} helper.
 *
 * <p>
 * {@code topic} is the Kafka topic to publish to. {@code key} is optional and used for partitioning
 * (when null Kafka's default partitioner decides). {@code payload} must be a valid JSON string; it
 * is sent as the record value with content-type {@code application/json} when the broker is
 * configured with a JSON deserializer.
 */
public record KafkaPublishIn(String topic, @Nullable String key, String payload) {
}
