package cbs.nova.starter.service.check;

import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.service.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@code postCheck: notify} — emits a best-effort log line for the named channel and,
 * opportunistically, publishes a {@link DomainEvent.PieceNotified} event on the existing
 * {@code dsl_events} mechanism (mirroring {@code DslReloadHandler}'s best-effort {@code
 * ReloadFailed} publish). Real notification sinks (Slack/email/webhooks) are Epic 3 — this hook is
 * log/event only.
 *
 * <p>
 * A publish failure is NOT swallowed here: it propagates so the pipeline can apply the check's
 * {@code onFailure} policy.
 */
@Slf4j
public class NotifyHook implements PostCheckHook {

  private final @Nullable ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  public NotifyHook(@Nullable ObjectProvider<DomainEventPublisher> eventPublisherProvider) {
    this.eventPublisherProvider = eventPublisherProvider;
  }

  @Override
  public String type() {
    return "notify";
  }

  @Override
  public void run(PostCheck check, Invocation invocation) {
    if (!(check instanceof PostCheck.NotifyCheck notify)) {
      throw new IllegalArgumentException("notify hook given a " + check.type() + " check");
    }
    log.info("[Piece notify] channel '{}' notified for piece '{}' ({} {}, correlationId={})",
            notify.channel(), invocation.piece().id(), invocation.context().method(),
            invocation.context().path(), invocation.correlationId());
    DomainEventPublisher publisher = eventPublisherProvider == null
            ? null
            : eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(new DomainEvent.PieceNotified(
            invocation.piece().id(), notify.channel(), null, invocation.correlationId()));
  }
}
