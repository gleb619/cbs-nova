package cbs.nova.starter.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * T769 email notification sink wiring. Kept in a separate, class-conditional configuration so that
 * applications which do not declare {@code spring-boot-starter-mail} do not fail to start when the
 * notification rules engine is otherwise enabled.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
public class EmailNotificationConfiguration {

  @Bean
  EmailRuleSink emailRuleSink(ObjectProvider<JavaMailSender> mailSenderProvider) {
    return new EmailRuleSink(mailSenderProvider);
  }
}
