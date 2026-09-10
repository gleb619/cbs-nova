package cbs.nova.starter.service;

import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class DomainEventPublisher {

  private final DslEventRepository repository;
  private final ObjectMapper objectMapper;

  public long publish(@NonNull DomainEvent event) {
    Instant now = Instant.now();
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(event);
    } catch (Exception e) {
      throw new IllegalStateException(
              "Failed to serialize domain event " + event.eventType()
                      + " for aggregate " + event.aggregateType() + "/" + event.aggregateId(),
              e);
    }
    Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : now;
    DslEventEntity row = new DslEventEntity(
            null,
            event.eventType(),
            event.aggregateType(),
            event.aggregateId(),
            event.correlationId(),
            payloadJson,
            event.schemaVersion(),
            occurredAt);
    repository.insert(row);
    log.debug("[DSL events] published {} aggregate={} id={} correlationId={}",
            event.eventType(), event.aggregateType(), event.aggregateId(),
            event.correlationId());
    return occurredAt.toEpochMilli();
  }
}
