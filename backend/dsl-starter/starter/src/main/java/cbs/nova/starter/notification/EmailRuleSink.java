package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * {@code email} sink. Sends a plain-text mail via an {@link JavaMailSender} bean when one is
 * present in the application (for instance when the operator has declared
 * {@code spring-boot-starter-mail} and configured {@code spring.mail.*}). When no sender bean
 * exists — the default for cbs-nova — the sink records a {@code noop} firing so rules remain
 * configurable and testable without an SMTP transport.
 *
 * <p>
 * {@link JavaMailSender} lives in {@code org.springframework:spring-context-support}, which is
 * pulled in transitively by the existing {@code spring-boot-starter-web} compileOnly bundle; no
 * runtime mail jar (jakarta mail / Angus mail) is forced on the starter — apps that need real email
 * must add {@code spring-boot-starter-mail} (or an equivalent implementation plus a
 * {@link JavaMailSender} bean) themselves.
 */
public class EmailRuleSink implements NotificationSink {

  static final String NOOP_DETAIL = "no mail transport configured";

  private final @Nullable JavaMailSender mailSender;

  public EmailRuleSink(ObjectProvider<JavaMailSender> mailSenderProvider) {
    this.mailSender = mailSenderProvider.getIfAvailable();
  }

  /**
   * Test-only constructor: pass a sender (or {@code null}) directly to skip the
   * {@link ObjectProvider} indirection.
   */
  EmailRuleSink(@Nullable JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  public String sinkType() {
    return "email";
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    if (mailSender == null) {
      return FiringOutcome.noop(NOOP_DETAIL, 0L);
    }
    String to = action.to();
    if (to == null || to.isBlank()) {
      return FiringOutcome.failure("missing 'to' recipient", 0L);
    }
    long start = System.nanoTime();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject("[%s] %s/%s".formatted(event.eventType(), event.aggregateType(),
            event.aggregateId()));
    message.setText(buildBody(rule, event));
    try {
      mailSender.send(message);
    } catch (MailException ex) {
      return FiringOutcome.failure(ex.getMessage(), elapsedMs(start));
    }
    return FiringOutcome.success("sent to " + recipientDomain(to), elapsedMs(start));
  }

  private static String buildBody(NotificationRuleEntity rule, DomainEvent event) {
    return "Notification rule: " + rule.name() + "%nEvent: " + event.eventType()
            + "%nAggregate: " + event.aggregateType() + "/" + event.aggregateId();
  }

  private static long elapsedMs(long startNanos) {
    return Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
  }

  /**
   * Returns just the domain part of an email address so a firing audit row never echoes the full
   * recipient back to operators with broad log access.
   */
  private static String recipientDomain(String to) {
    int at = to.indexOf('@');
    return at >= 0 && at + 1 < to.length() ? to.substring(at + 1) : "unknown";
  }
}
