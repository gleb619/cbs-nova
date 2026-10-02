package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Wiring specs for the email sink factory inside {@link NotificationConfiguration}: the bean must
 * be created in both bean states (a {@link JavaMailSender} bean is present or absent) and must hand
 * the resolved sender (or {@code null}) to {@link EmailRuleSink} unchanged so the sink keeps its
 * noop fallback when no transport is configured.
 */
class NotificationConfigurationTest {

  @Test
  void emailSinkBeanIsCreatedWhenNoMailSenderIsRegistered() {
    NotificationConfiguration config = new NotificationConfiguration();
    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.getIfAvailable()).thenReturn(null);

    EmailRuleSink sink = config.emailRuleSink(emptyProvider);

    assertThat(sink).isNotNull();
    FiringOutcome outcome = sink.deliver(rule("r"),
            new NotificationActionDto("email", null, null, "ops@example.com"), runFailed());
    assertThat(outcome.outcome()).isEqualTo("noop");
    assertThat(outcome.detail()).isEqualTo(EmailRuleSink.NOOP_DETAIL);
  }

  @Test
  void emailSinkBeanIsCreatedAndReceivesMailSenderWhenPresent() {
    NotificationConfiguration config = new NotificationConfiguration();
    JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sender);

    EmailRuleSink sink = config.emailRuleSink(provider);

    assertThat(sink).isNotNull();
    FiringOutcome outcome = sink.deliver(rule("r"),
            new NotificationActionDto("email", null, null, "ops@example.com"), runFailed());
    assertThat(outcome.outcome()).isEqualTo("success");
  }

  private static NotificationRuleEntity rule(String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(1L, name, true, "RunFailed", null, null, null, null, "[]",
            0, "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }
}
