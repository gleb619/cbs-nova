package cbs.nova.starter.events;

/**
 * Listener hook on the domain-event publish path. Implementations (e.g. the T565 notification rules
 * engine) are invoked by {@code cbs.nova.starter.service.DomainEventPublisher} after the event row
 * has been appended to {@code dsl_events}; the publisher wraps every call in a try/catch so a
 * failing listener can never break publishing.
 */
public interface DomainEventListener {

  /**
   * @param event
   *          the event that was just persisted
   * @param eventRowId
   *          the generated {@code dsl_events.id} of the persisted row
   */
  void onEvent(DomainEvent event, long eventRowId);
}
