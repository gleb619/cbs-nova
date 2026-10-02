package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Unit specs for {@link EmailRuleSink}. The sink is optional on {@link JavaMailSender}: when no
 * sender is configured (the cbs-nova out-of-the-box state) it records a {@code noop} firing so
 * rules remain configurable and testable; when a sender is present it sends exactly one
 * {@link SimpleMailMessage} and maps transport failures to a {@code failure} firing without letting
 * them escape the rule engine.
 */
class EmailRuleSinkTest {

  @Test
  void sinkTypeIsEmail() {
    assertThat(new EmailRuleSink((JavaMailSender) null).sinkType()).isEqualTo("email");
  }

  @Test
  void deliverRecordsNoopWhenMailSenderBeanIsAbsent() {
    EmailRuleSink sink = new EmailRuleSink((JavaMailSender) null);

    FiringOutcome outcome = sink.deliver(rule("loan-failures"),
            new NotificationActionDto("email", null, null, "ops@example.com"), runFailed());

    assertThat(outcome.outcome()).isEqualTo("noop");
    assertThat(outcome.detail()).isEqualTo(EmailRuleSink.NOOP_DETAIL);
    assertThat(outcome.durationMs()).isZero();
  }

  @Test
  void deliverSendsExactlyOneMessageWithExpectedFieldsWhenSenderPresent() {
    JavaMailSender sender = mock(JavaMailSender.class);
    EmailRuleSink sink = new EmailRuleSink(sender);
    NotificationRuleEntity rule = rule("loan-failures");
    NotificationActionDto action = new NotificationActionDto("email", null, null,
            "ops@example.com");
    DomainEvent.RunFailed event = runFailed();

    FiringOutcome outcome = sink.deliver(rule, action, event);

    ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
    verify(sender).send(captor.capture());
    SimpleMailMessage sent = captor.getValue();
    assertThat(sent.getTo()).containsExactly("ops@example.com");
    assertThat(sent.getSubject()).isEqualTo("[RunFailed] run/run-1");
    assertThat(sent.getText()).contains("Notification rule: loan-failures")
            .contains("Event: RunFailed")
            .contains("Aggregate: run/run-1");
    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("sent to example.com");
    assertThat(outcome.durationMs()).isPositive();
  }

  @Test
  void deliverMapsMailExceptionToFailureOutcomeWithoutPropagation() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new MailSendException("smtp connection refused"))
            .when(sender).send(any(SimpleMailMessage.class));
    EmailRuleSink sink = new EmailRuleSink(sender);

    FiringOutcome outcome = sink.deliver(rule("loan-failures"),
            new NotificationActionDto("email", null, null, "ops@example.com"), runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).contains("smtp connection refused");
    assertThat(outcome.durationMs()).isPositive();
  }

  @Test
  void deliverNeverSendsForBlankRecipientAndReportsFailureOutcome() {
    JavaMailSender sender = mock(JavaMailSender.class);
    EmailRuleSink sink = new EmailRuleSink(sender);

    FiringOutcome outcome = sink.deliver(rule("no-recipient"),
            new NotificationActionDto("email", null, null, "   "), runFailed());

    verify(sender, never()).send(any(SimpleMailMessage.class));
    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("missing 'to' recipient");
  }

  @Test
  void deliverNeverSendsForNullRecipientAndReportsFailureOutcome() {
    JavaMailSender sender = mock(JavaMailSender.class);
    EmailRuleSink sink = new EmailRuleSink(sender);

    FiringOutcome outcome = sink.deliver(rule("no-recipient"),
            new NotificationActionDto("email", null, null, null), runFailed());

    verify(sender, never()).send(any(SimpleMailMessage.class));
    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("missing 'to' recipient");
  }

  @Test
  void blankOrNullRecipientDoesNotThrowWhenSenderAbsent() {
    EmailRuleSink sink = new EmailRuleSink((JavaMailSender) null);

    assertThatCode(() -> {
      sink.deliver(rule("r"), new NotificationActionDto("email", null, null, null), runFailed());
      sink.deliver(rule("r"), new NotificationActionDto("email", null, null, ""), runFailed());
      sink.deliver(rule("r"), new NotificationActionDto("email", null, null, "   "),
              runFailed());
    }).doesNotThrowAnyException();
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
