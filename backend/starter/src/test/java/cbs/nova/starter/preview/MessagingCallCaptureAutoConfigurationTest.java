package cbs.nova.starter.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.ExternalCallTracker;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

class MessagingCallCaptureAutoConfigurationTest {

  private ExternalCallTracker tracker;
  private List<ExternalCallTracker.CallDetail> recorded;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    recorded = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.resetGlobalCounts();
  }

  @Test
  void producerRecordsSendCall() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    when(delegate.send(new ProducerRecord<>("orders", "key-1", "value-1")))
            .thenReturn(mock(Future.class));

    var capture = new MessagingCallCaptureProducer<>(delegate, tracker);
    tracker.startTracking(recorded);
    capture.send(new ProducerRecord<>("orders", "key-1", "value-1"));
    tracker.stopTracking();

    verify(delegate).send(new ProducerRecord<>("orders", "key-1", "value-1"));
    assertThat(recorded).hasSize(1);

    ExternalCallTracker.CallDetail call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallTracker.TYPE_MQ);
    assertThat(call.target()).isEqualTo("orders");
    assertThat(call.operation()).isEqualTo("send");

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) call.metadata().get("payload");
    assertThat(payload).containsEntry("topic", "orders");
    assertThat(payload).containsEntry("key", "key-1");
    assertThat(payload).containsEntry("value", "value-1");
  }

  @Test
  void producerRecordsSendWithCallback() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    Callback callback = mock(Callback.class);
    when(delegate.send(new ProducerRecord<>("events", "k", "v"), callback))
            .thenReturn(mock(Future.class));

    var capture = new MessagingCallCaptureProducer<>(delegate, tracker);
    tracker.startTracking(recorded);
    capture.send(new ProducerRecord<>("events", "k", "v"), callback);
    tracker.stopTracking();

    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("events");
  }

  @Test
  void producerFactoryWrapsCreatedProducer() {
    @SuppressWarnings("unchecked")
    Producer<String, String> realProducer = mock(Producer.class);
    when(realProducer.send(new ProducerRecord<>("metrics", "x"))).thenReturn(mock(Future.class));

    @SuppressWarnings("unchecked")
    ProducerFactory<String, String> delegate = mock(ProducerFactory.class);
    when(delegate.createProducer()).thenReturn(realProducer);

    var factory = new MessagingCallCaptureProducerFactory<>(delegate, tracker);
    tracker.startTracking(recorded);
    Producer<String, String> wrapped = factory.createProducer();
    wrapped.send(new ProducerRecord<>("metrics", "x"));
    tracker.stopTracking();

    verify(delegate).createProducer();
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("metrics");
  }

  @Test
  void beanPostProcessorWrapsProducerFactoryBean() {
    @SuppressWarnings("unchecked")
    Producer<String, String> realProducer = mock(Producer.class);
    when(realProducer.send(new ProducerRecord<>("logs", "y"))).thenReturn(mock(Future.class));

    ProducerFactory<String, String> bean = () -> realProducer;

    var postProcessor = new MessagingCallCaptureProducerFactoryBeanPostProcessor(tracker);
    Object processed = postProcessor.postProcessAfterInitialization(bean, "producerFactory");

    assertThat(processed).isInstanceOf(MessagingCallCaptureProducerFactory.class);
    assertThat(processed).isNotSameAs(bean);

    tracker.startTracking(recorded);
    ((ProducerFactory<String, String>) processed).createProducer()
            .send(new ProducerRecord<>("logs", "y"));
    tracker.stopTracking();

    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("logs");
  }

  @Test
  void beanPostProcessorLeavesOtherBeansUnchanged() {
    var postProcessor = new MessagingCallCaptureProducerFactoryBeanPostProcessor(tracker);
    Object plain = new Object();
    assertThat(postProcessor.postProcessAfterInitialization(plain, "plain")).isSameAs(plain);
  }

  @Test
  void autoconfigurationProducesBeanPostProcessor() {
    var config = new MessagingCallCaptureAutoConfiguration();
    var bean = MessagingCallCaptureAutoConfiguration
            .messagingCallCaptureProducerFactoryPostProcessor(tracker);
    assertThat(bean).isNotNull()
            .isInstanceOf(MessagingCallCaptureProducerFactoryBeanPostProcessor.class);
  }
}
