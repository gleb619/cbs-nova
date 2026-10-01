package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code kafkaPublish} helper.
 *
 * <p>
 * Confirms the record was handed to the broker. {@code topic} echoes the destination topic,
 * {@code partition} and {@code offset} come from the broker's {@code RecordMetadata}, and
 * {@code timestamp} records when the helper sent the record (record-level timestamp falls back to
 * the wall-clock time the helper captured at send-time).
 */
public record KafkaPublishOut(String topic, int partition, long offset,
        java.time.Instant timestamp) {
}
