package cbs.nova.starter.service;

import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.events.sink.DomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class DomainEventPublisher {

  private final DslEventRepository repository;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DomainEventListener> listeners;
  private final ObjectProvider<DomainEventSink> sinks;

  /**
   * Serializes the event, appends one row to {@code dsl_events}, then notifies every
   * {@link DomainEventListener} bean (T565: the notification rules engine) and every
   * {@link DomainEventSink} with the generated row id. Listener and sink calls are individually
   * guarded: a failing listener/sink is logged at warn level and can never break or roll back
   * publishing. Returns the {@code dsl_events.id} of the new row.
   */
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
    long rowId = repository.insert(row);
    log.debug("[DSL events] published {} aggregate={} id={} correlationId={}",
            event.eventType(), event.aggregateType(), event.aggregateId(),
            event.correlationId());
    notifyListeners(event, rowId);
    notifySinks(event, rowId);
    return rowId;
  }

  private void notifyListeners(DomainEvent event, long rowId) {
    listeners.orderedStream().forEach(listener -> {
      try {
        listener.onEvent(event, rowId);
      } catch (Exception e) {
        log.warn("[DSL events] listener {} failed for {} (row id {}): {}",
                listener.getClass().getName(), event.eventType(), rowId, e.getMessage());
      }
    });
  }

  private void notifySinks(DomainEvent event, long rowId) {
    sinks.orderedStream().forEach(sink -> {
      try {
        sink.onEvent(event, rowId);
      } catch (Exception e) {
        log.warn("[DSL events] sink {} failed for {} (row id {}): {}",
                sink.getClass().getName(), event.eventType(), rowId, e.getMessage());
      }
    });
  }
}
