package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit specs for {@link EmailRuleSink}. The sink is a deliberate NOOP placeholder (no mail
 * transport in the starter yet): it logs the would-be delivery and records a {@code noop} firing.
 * These tests pin that current behavior.
 */
class EmailRuleSinkTest {

  private final EmailRuleSink sink = new EmailRuleSink();

  @Test
  void sinkTypeIsEmail() {
    assertThat(sink.sinkType()).isEqualTo("email");
  }

  @Test
  void deliverRecordsNoopWithoutTouchingTransport() {
    NotificationRuleEntity rule = rule("loan-failures");
    NotificationActionDto action = new NotificationActionDto("email", null, null,
            "ops@example.com");

    FiringOutcome outcome = sink.deliver(rule, action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("noop");
    assertThat(outcome.detail()).isEqualTo("email sink is a placeholder");
    assertThat(outcome.durationMs()).isZero();
  }

  @Test
  void deliverToleratesMissingRecipientAndUrl() {
    NotificationRuleEntity rule = rule("no-recipient");
    NotificationActionDto action = new NotificationActionDto("email", null, null, null);

    assertThatCode(() -> sink.deliver(rule, action, runFailed())).doesNotThrowAnyException();

    FiringOutcome outcome = sink.deliver(rule, action, runFailed());
    assertThat(outcome.outcome()).isEqualTo("noop");
  }

  private static NotificationRuleEntity rule(String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(1L, name, true, "RunFailed", null, null, null, null, "[]", 0,
            "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }
}
