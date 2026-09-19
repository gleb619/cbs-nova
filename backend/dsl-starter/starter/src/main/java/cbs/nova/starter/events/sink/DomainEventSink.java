package cbs.nova.starter.events.sink;

import cbs.nova.starter.events.DomainEvent;

/**
 * Optional delivery channel for published domain events. A sink is invoked by
 * {@link cbs.nova.starter.service.DomainEventPublisher} after the event row has been appended to
 * {@code dsl_events}; failures are logged and must not break publishing.
 */
public interface DomainEventSink {

  /**
   * Deliver the persisted event to the sink.
   *
   * @param event
   *          the event that was just persisted
   * @param eventRowId
   *          the generated {@code dsl_events.id} of the persisted row
   */
  void onEvent(DomainEvent event, long eventRowId);
}
