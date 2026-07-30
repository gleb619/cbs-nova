package cbs.nova.starter.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.ExternalCallTracker;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Future;

class MessagingCallCaptureProducerTest {

  private ExternalCallTracker tracker;
  private ArrayList<ExternalCallTracker.CallDetail> recorded;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    recorded = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.stopMocking();
    tracker.resetGlobalCounts();
  }

  @Test
  void shortCircuitsSendWhenMockIsConfigured() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    @SuppressWarnings("unchecked")
    Future<RecordMetadata> mockFuture = mock(Future.class);

    var capture = new MessagingCallCaptureProducer<>(delegate, tracker);
    tracker.startTracking(recorded);
    tracker.startMocking(Map.of("mq:orders:send", (Object) mockFuture));
    Future<RecordMetadata> result = capture
            .send(new ProducerRecord<>("orders", "key-1", "value-1"));
    tracker.stopMocking();
    tracker.stopTracking();

    assertThat(result).isSameAs(mockFuture);
    verifyNoInteractions(delegate);
    assertThat(recorded).hasSize(1);

    ExternalCallTracker.CallDetail call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallTracker.TYPE_MQ);
    assertThat(call.target()).isEqualTo("orders");
    assertThat(call.operation()).isEqualTo("send");
    assertThat(call.metadata()).containsEntry("mockApplied", true);
  }

  @Test
  void shortCircuitsSendWithCallbackWhenMockIsConfigured() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    Callback callback = mock(Callback.class);
    @SuppressWarnings("unchecked")
    Future<RecordMetadata> mockFuture = mock(Future.class);

    var capture = new MessagingCallCaptureProducer<>(delegate, tracker);
    tracker.startTracking(recorded);
    tracker.startMocking(Map.of("mq:events:send", (Object) mockFuture));
    Future<RecordMetadata> result = capture.send(new ProducerRecord<>("events", "k", "v"),
            callback);
    tracker.stopMocking();
    tracker.stopTracking();

    assertThat(result).isSameAs(mockFuture);
    verifyNoInteractions(delegate);
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("events");
  }

  @Test
  void delegatesWhenNoMockIsConfigured() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    @SuppressWarnings("unchecked")
    Future<RecordMetadata> mockFuture = mock(Future.class);
    when(delegate.send(new ProducerRecord<>("metrics", "x"))).thenReturn(mockFuture);

    var capture = new MessagingCallCaptureProducer<>(delegate, tracker);
    tracker.startTracking(recorded);
    Future<RecordMetadata> result = capture.send(new ProducerRecord<>("metrics", "x"));
    tracker.stopTracking();

    assertThat(result).isSameAs(mockFuture);
    verify(delegate).send(new ProducerRecord<>("metrics", "x"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("metrics");
  }
}
