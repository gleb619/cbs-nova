package cbs.nova.starter.preview;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@RequiredArgsConstructor
public class MessagingCallCaptureProducer<K, V> implements Producer<K, V> {

  private static final String TYPE_MESSAGING = "messaging";

  private final Producer<K, V> delegate;
  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
    recordCall(record);
    return delegate.send(record);
  }

  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
    recordCall(record);
    return delegate.send(record, callback);
  }

  @Override
  public void initTransactions() {
    delegate.initTransactions();
  }

  @Override
  public void beginTransaction() throws ProducerFencedException {
    delegate.beginTransaction();
  }

  @Override
  public void sendOffsetsToTransaction(
          Map<TopicPartition, OffsetAndMetadata> offsets,
          ConsumerGroupMetadata groupMetadata)
          throws ProducerFencedException {
    delegate.sendOffsetsToTransaction(offsets, groupMetadata);
  }

  @Override
  public void commitTransaction() throws ProducerFencedException {
    delegate.commitTransaction();
  }

  @Override
  public void abortTransaction() throws ProducerFencedException {
    delegate.abortTransaction();
  }

  @Override
  public void registerMetricForSubscription(KafkaMetric metric) {
    delegate.registerMetricForSubscription(metric);
  }

  @Override
  public void unregisterMetricFromSubscription(KafkaMetric metric) {
    delegate.unregisterMetricFromSubscription(metric);
  }

  @Override
  public void flush() {
    delegate.flush();
  }

  @Override
  public List<PartitionInfo> partitionsFor(String topic) {
    return delegate.partitionsFor(topic);
  }

  @Override
  public Map<MetricName, ? extends Metric> metrics() {
    return delegate.metrics();
  }

  @Override
  public Uuid clientInstanceId(Duration timeout) {
    return delegate.clientInstanceId(timeout);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public void close(Duration timeout) {
    delegate.close(timeout);
  }

  private void recordCall(ProducerRecord<K, V> record) {
    if (record == null) {
      externalCallRecorder.record(TYPE_MESSAGING, "unknown", "send", null);
      return;
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("topic", record.topic());
    payload.put("key", record.key());
    payload.put("value", record.value());
    payload.put("partition", record.partition());
    payload.put("timestamp", record.timestamp());
    payload.put("headers", record.headers() != null ? record.headers().toArray().length : 0);

    externalCallRecorder.record(TYPE_MESSAGING, record.topic(), "send", payload);
  }
}
