package cbs.nova.starter.notification;

import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.webhook.WebhookDispatcher;
import cbs.nova.starter.webhook.WebhookProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

// NOTE: deliberately NOT annotated @Configuration — like DslRunRepositoryConfiguration this class
// is wired only through the @Import in DslRootAutoConfiguration. A @Configuration class in the
// scanned cbs.nova.starter tree would be picked up by host/test component scans BEFORE the
// DataSource bean definition exists, and its @ConditionalOnBean(DataSource.class) methods would
// silently back off.

/**
 * T565 notification rules engine wiring. Everything is conditional on a {@link DataSource}: the
 * rule store and firing audit are JDBC-backed.
 *
 * <p>
 * Like {@code DslRunRepositoryConfiguration}, this class deliberately carries NO
 * {@code @Configuration} stereotype: it is only ever imported from
 * {@code DslRootAutoConfiguration}, so its {@code @ConditionalOnBean(DataSource)} beans are
 * evaluated in the auto-configuration phase (after {@code DataSourceAutoConfiguration}) and never
 * via component scanning — where the DataSource bean definition would not exist yet.
 *
 * <p>
 * The engine bean implements {@code DomainEventListener}, so the {@code DomainEventPublisher} picks
 * it up via {@code ObjectProvider<DomainEventListener>} once the event row is appended.
 */
public class NotificationConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  NotificationRuleRepository notificationRuleRepository(NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    return new NotificationRuleRepository(jdbcTemplate, dslQueries);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  NotificationRuleFiringRepository notificationRuleFiringRepository(
          NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    return new NotificationRuleFiringRepository(jdbcTemplate, dslQueries);
  }

  @Bean
  @ConditionalOnBean(NotificationRuleRepository.class)
  NotificationRuleService notificationRuleService(NotificationRuleRepository repository,
          ObjectMapper objectMapper, ObjectProvider<DslAuditService> auditService) {
    return new NotificationRuleService(repository, objectMapper, auditService);
  }

  @Bean
  @ConditionalOnBean(NotificationRuleService.class)
  WebhookRuleSink webhookRuleSink(WebhookDispatcher dispatcher) {
    return new WebhookRuleSink(dispatcher);
  }

  @Bean
  @ConditionalOnBean(NotificationRuleService.class)
  EmailRuleSink emailRuleSink() {
    return new EmailRuleSink();
  }

  @Bean
  @ConditionalOnBean(NotificationRuleService.class)
  GenericJsonRuleSink slackRuleSink(@Qualifier("webhookHttpClient") HttpClient webhookHttpClient,
          ObjectMapper objectMapper, WebhookProperties properties) {
    return new GenericJsonRuleSink("slack", webhookHttpClient, objectMapper,
            properties.getTimeout());
  }

  @Bean
  @ConditionalOnBean(NotificationRuleService.class)
  GenericJsonRuleSink pagerDutyRuleSink(
          @Qualifier("webhookHttpClient") HttpClient webhookHttpClient,
          ObjectMapper objectMapper, WebhookProperties properties) {
    return new GenericJsonRuleSink("pagerduty", webhookHttpClient, objectMapper,
            properties.getTimeout());
  }

  @Bean(name = "cbsNovaNotificationExecutor", destroyMethod = "shutdown")
  ThreadPoolTaskExecutor cbsNovaNotificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("cbs-nova-notification-");
    // Same bounded-queue + caller-runs policy as the webhook delivery executor: sink work is
    // offloaded from the publish path, but a saturated queue blocks the caller rather than
    // dropping notifications.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean
  @ConditionalOnBean(NotificationRuleService.class)
  NotificationRuleEngine notificationRuleEngine(NotificationRuleRepository ruleRepository,
          NotificationRuleFiringRepository firingRepository, NotificationRuleService ruleService,
          List<NotificationSink> sinks,
          ThreadPoolTaskExecutor cbsNovaNotificationExecutor) {
    return new NotificationRuleEngine(ruleRepository, firingRepository, ruleService, sinks,
            cbsNovaNotificationExecutor);
  }
}
