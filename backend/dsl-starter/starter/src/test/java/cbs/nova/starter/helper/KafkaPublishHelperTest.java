package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.KafkaPublishIn;
import cbs.nova.starter.helper.model.KafkaPublishOut;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

class KafkaPublishHelperTest {

  private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final KafkaPublishHelper helper = new KafkaPublishHelper(kafkaTemplate, mapper);

  @Test
  void publishSendsJsonPayloadToTopicWithOptionalKey() {
    String topic = "orders";
    String key = "order-42";
    String payload = "{\"id\":42,\"status\":\"confirmed\"}";
    stubSend(topic, 3, 17L);

    Result<KafkaPublishOut> result = execute(new KafkaPublishIn(topic, key, payload));

    assertThat(result.isSuccess()).isTrue();
    KafkaPublishOut out = result.value();
    assertThat(out.topic()).isEqualTo(topic);
    assertThat(out.partition()).isEqualTo(3);
    assertThat(out.offset()).isEqualTo(17L);
    assertThat(out.timestamp()).isBetween(Instant.now().minusSeconds(5),
            Instant.now().plusSeconds(5));

    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor
            .forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, String> sent = recordCaptor.getValue();
    assertThat(sent.topic()).isEqualTo(topic);
    assertThat(sent.key()).isEqualTo(key);
    assertThat(sent.value()).isEqualTo(payload);
    assertThat(sent.headers().lastHeader("Content-Type").value())
            .isEqualTo("application/json".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void publishWithoutKeyIsAllowed() {
    stubSend("orders", 0, 0L);

    Result<KafkaPublishOut> result = execute(new KafkaPublishIn("orders", null, "{}"));

    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor
            .forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    assertThat(recordCaptor.getValue().key()).isNull();
  }

  @Test
  void invalidJsonPayloadFails() {
    Result<KafkaPublishOut> result = execute(new KafkaPublishIn("orders", null, "{not json"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("kafkaPublish.payload is not valid JSON:");
  }

  @Test
  void missingTopicFails() {
    Result<KafkaPublishOut> result = execute(new KafkaPublishIn("", null, "{}"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("kafkaPublish.topic is required");
  }

  @Test
  void missingPayloadFails() {
    Result<KafkaPublishOut> result = execute(new KafkaPublishIn("orders", null, ""));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("kafkaPublish.payload is required");
  }

  @Test
  void missingKafkaTemplateBeanFailsWithoutTouchingBroker() {
    KafkaPublishHelper withoutTemplate = new KafkaPublishHelper(
            (KafkaTemplate<String, String>) null,
            mapper);

    Result<KafkaPublishOut> result = withoutTemplate.execute(
            SimpleContext.builder(new KafkaPublishIn("orders", null, "{}"))
                    .mode(ExecutionMode.PREVIEW).build());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalStateException.class);
    assertThat(result.cause()).hasMessageStartingWith(
            "kafkaPublish: KafkaTemplate bean is not available");
  }

  @Test
  void sendFailurePropagates() {
    RuntimeException boom = new RuntimeException("broker offline");
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(boom);
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

    Result<KafkaPublishOut> result = execute(new KafkaPublishIn("orders", null, "{}"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isSameAs(boom);
  }

  private void stubSend(String topic, int partition, long offset) {
    RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, partition), offset, 0,
            Instant.now().toEpochMilli(), 0, 0);
    SendResult<String, String> sendResult = new SendResult<>(null, metadata);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));
  }

  private Result<KafkaPublishOut> execute(KafkaPublishIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
