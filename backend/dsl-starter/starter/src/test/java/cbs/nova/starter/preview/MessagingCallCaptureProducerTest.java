package cbs.nova.starter.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

class MessagingCallCaptureProducerTest {
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();

  private RunIdKeyedExternalCallRecorder recorder;
  private List<ExternalCall> recorded;

  @BeforeEach
  void setUp() {
    recorder = new RunIdKeyedExternalCallRecorder(dryRunLoggingContext, null);
    recorder.resetGlobalCounts();
    recorded = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    recorder.resetGlobalCounts();
  }

  @Test
  void recordsAndDelegatesSend() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    @SuppressWarnings("unchecked")
    Future<RecordMetadata> delegateFuture = mock(Future.class);
    when(delegate.send(new ProducerRecord<>("orders", "key-1", "value-1")))
            .thenReturn(delegateFuture);

    var capture = new MessagingCallCaptureProducer<>(delegate, recorder);
    recorder.startRun("run-1");
    Future<RecordMetadata> result = capture
            .send(new ProducerRecord<>("orders", "key-1", "value-1"));
    recorded.addAll(recorder.finishRun("run-1"));

    assertThat(result).isSameAs(delegateFuture);
    verify(delegate).send(new ProducerRecord<>("orders", "key-1", "value-1"));
    assertThat(recorded).hasSize(1);

    ExternalCall call = recorded.get(0);
    assertThat(call.type()).isEqualTo(ExternalCallRecorder.TYPE_MQ);
    assertThat(call.target()).isEqualTo("orders");
    assertThat(call.operation()).isEqualTo("send");
  }

  @Test
  void recordsAndDelegatesSendWithCallback() {
    @SuppressWarnings("unchecked")
    Producer<String, String> delegate = mock(Producer.class);
    Callback callback = mock(Callback.class);
    @SuppressWarnings("unchecked")
    Future<RecordMetadata> delegateFuture = mock(Future.class);
    when(delegate.send(new ProducerRecord<>("events", "k", "v"), callback))
            .thenReturn(delegateFuture);

    var capture = new MessagingCallCaptureProducer<>(delegate, recorder);
    recorder.startRun("run-1");
    Future<RecordMetadata> result = capture.send(new ProducerRecord<>("events", "k", "v"),
            callback);
    recorded.addAll(recorder.finishRun("run-1"));

    assertThat(result).isSameAs(delegateFuture);
    verify(delegate).send(new ProducerRecord<>("events", "k", "v"), callback);
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).target()).isEqualTo("events");
  }
}
