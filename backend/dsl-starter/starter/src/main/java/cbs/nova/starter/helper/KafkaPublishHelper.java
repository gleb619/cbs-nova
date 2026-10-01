package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.KafkaPublishIn;
import cbs.nova.starter.helper.model.KafkaPublishOut;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an arbitrary JSON payload to a Kafka topic.
 *
 * <p>
 * Reuses the existing {@link KafkaTemplate} Spring bean; no extra broker connection is created. The
 * helper validates that {@code payload} is valid JSON before sending it as a record value with a
 * {@code Content-Type: application/json} header. The send future is awaited with a bounded timeout
 * so a stuck broker cannot pin the calling activity.
 *
 * <p>
 * Opt-in: spring-kafka is a {@code compileOnly} dependency of the starter and the
 * {@code starter-launcher} excludes {@code KafkaAutoConfiguration}, so apps that do not declare a
 * {@code KafkaTemplate} bean see an explicit {@code IllegalStateException} instead of an NPE. To
 * enable, drop the launcher-level Kafka auto-config exclude and add a runtime spring-kafka
 * dependency, then expose a {@code KafkaTemplate} bean (Spring Boot auto-configures one when
 * {@code spring.kafka.bootstrap-servers} is set).
 */
@SpringHelper(name = "kafkaPublish")
public class KafkaPublishHelper implements Executable<KafkaPublishIn, KafkaPublishOut> {

  private static final long SEND_TIMEOUT_MILLIS = 5_000L;
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String CONTENT_TYPE_JSON = "application/json";

  private final @Nullable KafkaTemplate<String, String> kafkaTemplate;
  private final @NonNull ObjectMapper mapper;
  private final long sendTimeoutMillis;

  @Autowired
  public KafkaPublishHelper(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
          ObjectMapper mapper) {
    this(kafkaTemplateProvider.getIfAvailable(), mapper, SEND_TIMEOUT_MILLIS);
  }

  KafkaPublishHelper(@Nullable KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
    this(kafkaTemplate, mapper, SEND_TIMEOUT_MILLIS);
  }

  KafkaPublishHelper(@Nullable KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper,
          long sendTimeoutMillis) {
    this.kafkaTemplate = kafkaTemplate;
    this.mapper = mapper;
    this.sendTimeoutMillis = sendTimeoutMillis;
  }

  @Override
  public @NonNull Result<KafkaPublishOut> execute(@NonNull Context<KafkaPublishIn> ctx) {
    try {
      KafkaPublishIn input = ctx.body();
      validate(input);

      if (kafkaTemplate == null) {
        return Result.failure(new IllegalStateException(
                "kafkaPublish: KafkaTemplate bean is not available; Kafka is not configured"));
      }

      ProducerRecord<String, String> record = new ProducerRecord<>(
              input.topic(),
              null,
              null,
              input.key(),
              input.payload(),
              java.util.List.of(new RecordHeader(CONTENT_TYPE_HEADER,
                      CONTENT_TYPE_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      Instant sentAt = Instant.now();
      SendResult<String, String> sendResult = kafkaTemplate.send(record)
              .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);

      int partition = sendResult.getRecordMetadata() != null
              ? sendResult.getRecordMetadata().partition()
              : -1;
      long offset = sendResult.getRecordMetadata() != null
              ? sendResult.getRecordMetadata().offset()
              : -1L;

      return Result.success(new KafkaPublishOut(input.topic(), partition, offset, sentAt));
    } catch (RuntimeException e) {
      return Result.failure(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure(e);
    } catch (java.util.concurrent.ExecutionException
            | java.util.concurrent.TimeoutException e) {
      return Result.failure(
              e instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null
                      ? ee.getCause()
                      : e);
    }
  }

  private void validate(@NonNull KafkaPublishIn input) {
    if (input.topic() == null || input.topic().isBlank()) {
      throw new IllegalArgumentException("kafkaPublish.topic is required");
    }
    if (input.payload() == null || input.payload().isBlank()) {
      throw new IllegalArgumentException("kafkaPublish.payload is required");
    }
    try {
      mapper.readTree(input.payload());
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "kafkaPublish.payload is not valid JSON: " + e.getOriginalMessage(), e);
    }
  }
}
