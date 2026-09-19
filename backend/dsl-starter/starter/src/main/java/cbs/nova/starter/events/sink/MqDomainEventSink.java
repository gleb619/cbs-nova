package cbs.nova.starter.events.sink;

import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.mq.DomainEventMqEnvelope;
import cbs.nova.starter.persistence.DslEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * RabbitMQ-backed {@link DomainEventSink}. Publishes a canonical JSON envelope to the configured
 * topic exchange. Successful publishes are tracked in the transactional outbox column
 * {@code dsl_events.mq_published} so the retry task can replay failures.
 */
@Slf4j
@RequiredArgsConstructor
public class MqDomainEventSink implements DomainEventSink {

  private final AmqpTemplate amqpTemplate;
  private final ObjectMapper objectMapper;
  private final DslEventRepository repository;
  private final MqEventProperties properties;

  @Override
  public void onEvent(DomainEvent event, long eventRowId) {
    if (!properties.enabled()) {
      return;
    }
    try {
      JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));
      send(event.eventType(), event.aggregateType(), event.aggregateId(),
              event.correlationId(), event.schemaVersion(), event.occurredAt(),
              eventRowId, payload);
    } catch (Exception e) {
      log.warn("[DSL events MQ] failed to publish {} rowId={}: {}",
              event.eventType(), eventRowId, e.getMessage());
    }
  }

  /**
   * Retry entry point: publishes a row straight from the outbox without re-inflating the concrete
   * {@link DomainEvent} subtype.
   */
  public void onEvent(DslEventEntity row) {
    if (!properties.enabled()) {
      return;
    }
    try {
      JsonNode payload = objectMapper.readTree(row.payloadJson());
      send(row.eventType(), row.aggregateType(), row.aggregateId(), row.correlationId(),
              row.schemaVersion(), row.createdAt(), row.id(), payload);
    } catch (Exception e) {
      log.warn("[DSL events MQ] failed to replay {} rowId={}: {}",
              row.eventType(), row.id(), e.getMessage());
    }
  }

  private void send(String eventType, String aggregateType, String aggregateId,
          @Nullable String correlationId, int schemaVersion, @Nullable Instant occurredAt,
          long eventRowId, JsonNode payload) {
    String exchange = properties.exchange();
    if (exchange == null || exchange.isBlank()) {
      log.warn("[DSL events MQ] enabled but no exchange configured; dropping {}", eventType);
      return;
    }
    String routingKey = properties.effectiveRoutingKey(eventType);
    DomainEventMqEnvelope envelope = new DomainEventMqEnvelope(
            eventType, aggregateType, aggregateId, correlationId,
            schemaVersion, occurredAt, eventRowId, payload);
    byte[] body = objectMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8);
    MessageProperties props = MessagePropertiesBuilder.newInstance()
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeaderIfAbsent("eventType", eventType)
            .setHeaderIfAbsent("aggregateType", aggregateType)
            .setHeaderIfAbsent("aggregateId", aggregateId)
            .setHeaderIfAbsent("correlationId", correlationId)
            .build();
    amqpTemplate.send(exchange, routingKey, new Message(body, props));
    repository.markPublished(eventRowId);
    log.debug("[DSL events MQ] sent {} to exchange={} routingKey={} rowId={}",
            eventType, exchange, routingKey, eventRowId);
  }
}
