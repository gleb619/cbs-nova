package cbs.nova.starter.preview;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serializer;
import org.jspecify.annotations.NonNull;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link ProducerFactory} decorator that wraps every created Kafka {@link Producer} with
 * {@link MessagingCallCaptureProducer} so message publishing is recorded as an external "messaging"
 * call.
 */
@RequiredArgsConstructor
public class MessagingCallCaptureProducerFactory<K, V> implements ProducerFactory<K, V> {

  private final ProducerFactory<K, V> delegate;
  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public Producer<K, V> createProducer() {
    return wrap(delegate.createProducer());
  }

  @Override
  public Producer<K, V> createProducer(String clientId) {
    return wrap(delegate.createProducer(clientId));
  }

  @Override
  public Producer<K, V> createNonTransactionalProducer() {
    return wrap(delegate.createNonTransactionalProducer());
  }

  @Override
  public boolean transactionCapable() {
    return delegate.transactionCapable();
  }

  @Override
  public void closeThreadBoundProducer() {
    delegate.closeThreadBoundProducer();
  }

  @Override
  public void reset() {
    delegate.reset();
  }

  @Override
  public Map<String, Object> getConfigurationProperties() {
    return delegate.getConfigurationProperties();
  }

  @Override
  public Supplier<Serializer<V>> getValueSerializerSupplier() {
    return delegate.getValueSerializerSupplier();
  }

  @Override
  public Supplier<Serializer<K>> getKeySerializerSupplier() {
    return delegate.getKeySerializerSupplier();
  }

  @Override
  public boolean isProducerPerThread() {
    return delegate.isProducerPerThread();
  }

  @Override
  public String getTransactionIdPrefix() {
    return delegate.getTransactionIdPrefix();
  }

  @Override
  public Duration getPhysicalCloseTimeout() {
    return delegate.getPhysicalCloseTimeout();
  }

  @Override
  public void addListener(ProducerFactory.Listener<K, V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void addListener(int index, ProducerFactory.Listener<K, V> listener) {
    delegate.addListener(index, listener);
  }

  @Override
  public boolean removeListener(ProducerFactory.Listener<K, V> listener) {
    return delegate.removeListener(listener);
  }

  @Override
  public List<ProducerFactory.Listener<K, V>> getListeners() {
    return delegate.getListeners();
  }

  @Override
  public void addPostProcessor(ProducerPostProcessor<K, V> postProcessor) {
    delegate.addPostProcessor(postProcessor);
  }

  @Override
  public boolean removePostProcessor(ProducerPostProcessor<K, V> postProcessor) {
    return delegate.removePostProcessor(postProcessor);
  }

  @Override
  public List<ProducerPostProcessor<K, V>> getPostProcessors() {
    return delegate.getPostProcessors();
  }

  @Override
  public void updateConfigs(Map<String, Object> configs) {
    delegate.updateConfigs(configs);
  }

  @Override
  public void removeConfig(String configKey) {
    delegate.removeConfig(configKey);
  }

  @Override
  public Serializer<K> getKeySerializer() {
    return delegate.getKeySerializer();
  }

  @Override
  public Serializer<V> getValueSerializer() {
    return delegate.getValueSerializer();
  }

  @Override
  public ProducerFactory<K, V> copyWithConfigurationOverride(Map<String, Object> configs) {
    return delegate.copyWithConfigurationOverride(configs);
  }

  private Producer<K, V> wrap(Producer<K, V> producer) {
    return new MessagingCallCaptureProducer<>(producer, externalCallRecorder);
  }
}
