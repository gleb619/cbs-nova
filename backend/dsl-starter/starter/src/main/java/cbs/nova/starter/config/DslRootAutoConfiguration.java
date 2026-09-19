package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.config.router.DslRouterConfiguration;
import cbs.nova.starter.security.ProductionSecurityPostureValidator;
import cbs.nova.starter.security.SecurityPostureReporter;
import cbs.nova.starter.webhook.WebhookConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.events.mq.MqEventConfiguration;
import cbs.nova.starter.notification.NotificationConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Import({
    RequestIdFilterConfiguration.class,
    LoggingConfiguration.class,
    DryRunLoggingConfiguration.class,
    DslConfiguration.class,
    DslWorkerConfiguration.class,
    TemporalConfiguration.class,
    DataSourceCallConfiguration.class,
    FeignCallConfiguration.class,
    PreviewConfiguration.class,
    PreviewCacheConfiguration.class,
    MessagingCallCaptureConfiguration.class,
    PreviewMetricsConfiguration.class,
    DslRunRepositoryConfiguration.class,
    DslRouterConfiguration.class,
    WebhookConfiguration.class,
    DslRunRetentionConfiguration.class,
    DslRunReconciliationConfiguration.class,
    DslMaintenanceConfiguration.class,
    DslErrorHandlingConfiguration.class,
    SpringHelperConfiguration.class,
    ApiKeyAuthFilterConfiguration.class,
    RateLimitFilterConfiguration.class,
    RbacFilterConfiguration.class,
    SecurityConfiguration.class,
    ApiKeyAuthMisconfigurationWarning.class,
    ProductionSecurityPostureValidator.class,
    SecurityPostureReporter.class,
    ExplainBudgetStageWarmupConfiguration.class,
    SentryStatusReporter.class,
    DslHealthIndicatorConfiguration.class,
    BuilderClientConfiguration.class,
    VhsConfiguration.class,
    NotificationConfiguration.class,
    MqEventConfiguration.class,
})
@EnableConfigurationProperties({DslProperties.class, CbsNovaCacheProperties.class,
    CbsHealthProperties.class, MqEventProperties.class})
public class DslRootAutoConfiguration {
}
